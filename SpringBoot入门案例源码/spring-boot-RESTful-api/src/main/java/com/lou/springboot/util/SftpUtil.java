package com.lou.springboot.util;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

@Component
@Slf4j
public class SftpUtil {
    //ip地址
    @Value("${sftp.client.host}")
    private String host;

    //端口号
    @Value("${sftp.client.port}")
    private Integer port;

    //用户名
    @Value("${sftp.client.username}")
    private String username;

    //密码
    @Value("${sftp.client.password}")
    private String password;

    //session链接超时时间
    @Value("${sftp.client.sessionConnectTimeout}")
    private Integer sessionConnectTimeout;

    //channel链接超时时间
    @Value("${sftp.client.channelConnectedTimeout}")
    private Integer channelConnectedTimeout;


    /**
     * 检查SFTP目录或文件是否存在
     * @param remotePath
     * @return
     */
    public boolean checkFileExist(String remotePath) {
        ChannelSftp channelSftp = loginSftp();
        try {
            channelSftp.ls(remotePath);

            return true;
        } catch (SftpException e) {
            if (e.getMessage().toLowerCase().equals("no such file")) {
                return false;
            }
        }
        return false;
    }

    /**
     * 文件上传
     * @param localFilePath
     * @param remoteDirPath
     * @return
     */
    public String uploadFile(String localFilePath, String remoteDirPath) {
        ChannelSftp channelSftp = loginSftp();
        try {
            boolean dirs = createDirs(remoteDirPath, channelSftp);
            if (!dirs) {
                log.error("Remote path error. path:{}", remoteDirPath);
                return null;
            }
            channelSftp.put(localFilePath, remoteDirPath);

            String remoteFilePath = remoteDirPath+"/"+new File(localFilePath).getName();

            log.info("upload file:::{}", remoteFilePath);

            return remoteFilePath;
        } catch(SftpException ex) {
            log.error("Error upload file", ex);
        } finally {
            logoutSftp(channelSftp);
        }
        return null;
    }


    /**
     * 文件下载
     * @param localDirPath
     * @param remoteFilePath
     * @return
     */
    public String downloadFile(String localDirPath, String remoteFilePath) {
        ChannelSftp channelSftp = loginSftp();
        OutputStream outputStream = null;
        try {
            String fileName = remoteFilePath.substring(remoteFilePath.lastIndexOf("/") + 1);

            if(!new File(localDirPath).exists()){
                new File(localDirPath).mkdirs();
            }

            String localFilePath = localDirPath+"/"+fileName;
            File file = new File(localFilePath);
            outputStream = new FileOutputStream(file);
            channelSftp.get(remoteFilePath, outputStream);

            file.createNewFile();
            return localFilePath;
        } catch(SftpException | IOException ex) {
            ex.printStackTrace();
        } finally {
            logoutSftp(channelSftp);
            if(outputStream!=null){
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }



    /**
     * 登录SFTP
     * @return
     */
    private ChannelSftp loginSftp() {
        JSch jsch = new JSch();
        try {
            Session sshSession = jsch.getSession(username, host, port);
            sshSession.setPassword(password);
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.connect(sessionConnectTimeout);
            Channel channel = sshSession.openChannel("sftp");
            channel.connect(channelConnectedTimeout);
            ChannelSftp channelSftp = (ChannelSftp) channel;
            return channelSftp;
        } catch (JSchException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 关闭SFTP
     * @param channelSftp
     */
    private void logoutSftp(ChannelSftp channelSftp) {
        try {
            if( channelSftp == null){
                return;
            }
            if(channelSftp.isConnected()){
                channelSftp.disconnect();
            }
            if(channelSftp.getSession() != null){
                channelSftp.getSession().disconnect();
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 创建SFTP目录，如果目录不存在就创建
     * @param dirPath
     * @param sftp
     * @return
     */
    private boolean createDirs(String dirPath, ChannelSftp sftp) {
        if (StringUtils.isNotBlank(dirPath)) {
            String[] dirs = Arrays.stream(dirPath.split("/")).filter(StringUtils::isNotBlank).toArray(String[]::new);
            for (String dir : dirs) {
                try {
                    sftp.cd(dir);
                    log.info("Change directory {}", dir);
                } catch (Exception e) {
                    try {
                        sftp.mkdir(dir);
                        log.info("Create directory {}", dir);
                    } catch (SftpException e1) {
                        log.error("Create directory failure, directory:{}", dir, e1);
                        e1.printStackTrace();
                        return false;
                    }
                    try {
                        sftp.cd(dir);
                        log.info("Change directory {}", dir);
                    } catch (SftpException e1) {
                        log.error("Change directory failure, directory:{}", dir, e1);
                        e1.printStackTrace();
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

}

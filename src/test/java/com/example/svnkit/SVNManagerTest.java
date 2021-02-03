package com.example.svnkit;

import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class SVNManagerTest {

    final String url = "http://192.168.1.1/svn/xxx/xxx/xxx/projectName";
    final String username = "username";
    final String password = "password";
    SVNManager svnManager = null;
    DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

    @Before
    public void login() throws SVNException {
        svnManager = new SVNManager();
        svnManager.createSession(url, username, password);
    }

    /**
     * 获取起始与结束时间之间的提交记录
     * (包含提交的版本、时间、用户、提交日志、提交的文件)(按日期排序)
     */
    @Test
    public void testGetLogsBetweenDate() {
        try {
            LocalDateTime start = LocalDateTime.parse("2021-01-22 17:15:00", dateTimeFormatter);
            LocalDateTime end = LocalDateTime.parse("2021-01-27 17:15:00", dateTimeFormatter);
            List<SVNLogEntry> logs = svnManager.getLogs(start, end);
            logs.parallelStream()
                    .filter(item -> !item.getAuthor().equalsIgnoreCase("svn-tool"))
                    .sorted(Comparator.comparing(SVNLogEntry::getDate))
                    .forEach(log -> {
                        System.out.println(log.getRevision() + " " + LocalDateTime.fromDateFields(log.getDate()).toString(dateTimeFormatter)
                                + " " + log.getAuthor() + " " + log.getMessage());
                        log.getChangedPaths().keySet().forEach(System.out::println);
                    });
        } catch (SVNException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void testGetLogsBetweenDateWithAuthor() {
        try {
            LocalDateTime start = LocalDateTime.parse("2021-01-22 17:15:00", dateTimeFormatter);
            LocalDateTime end = LocalDateTime.parse("2021-01-27 17:15:00", dateTimeFormatter);
            String author = "xxx";
            List<SVNLogEntry> logs = svnManager.getLogs(start, end, author);
            logs.parallelStream()
                    .filter(item -> !item.getAuthor().equalsIgnoreCase("svn-tool"))
                    .sorted(Comparator.comparing(SVNLogEntry::getDate))
                    .forEach(log -> {
                        System.out.println(log.getRevision() + " " + LocalDateTime.fromDateFields(log.getDate()).toString(dateTimeFormatter)
                                + " " + log.getAuthor() + " " + log.getMessage());
                        log.getChangedPaths().keySet().forEach(System.out::println);
                    });
        } catch (SVNException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    /**
     * 根据起始结束时间及提交者用户名,获取该用户在这个时间区间内提交的文件名(去重并按文件路径排序)
     */
    @Test
    public void testGetDistinctLogsBetweenDateWithAuthor() {
        try {
            LocalDateTime start = LocalDateTime.parse("2021-01-22 17:15:00", dateTimeFormatter);
            LocalDateTime end = LocalDateTime.parse("2021-01-27 17:15:00", dateTimeFormatter);
            String author = "xxx";
            List<SVNLogEntry> logs = svnManager.getLogs(start, end, author);
            logs.parallelStream()
                    .map(log -> log.getChangedPaths().keySet().toArray(new String[0]))
                    .flatMap(Arrays::stream)
                    .distinct()
                    .sorted(Comparator.naturalOrder())
                    .forEach(System.out::println);
        } catch (SVNException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    /**
     * 根据起始结束时间及提交者用户名,将该用户在这个时间区间内提交的文件进行前后版本对比,生成比较日志文件
     * (先将该用户的所有提交文件过滤去重,再将这些文件的起始时间的前版本与结束时间的版本进行比较)
     * (包含其他人对这些文件的修改)
     */
    @Test
    public void testGetChangeLogBetweenDate(){
        try {
            LocalDateTime start = LocalDateTime.parse("2021-01-22 17:15:00", dateTimeFormatter);
            LocalDateTime end = LocalDateTime.parse("2021-01-27 17:15:00", dateTimeFormatter);
            String author = "xxx";
            File changeLog = svnManager.getChangeLog(start, end, author);
            System.out.println("生成的比较日志文件路径: " + changeLog.getAbsolutePath());
        } catch (SVNException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void testStaticsCodeAdd(){
        try {
            LocalDateTime start = LocalDateTime.parse("2021-01-22 17:15:00", dateTimeFormatter);
            LocalDateTime end = LocalDateTime.parse("2021-01-27 17:15:00", dateTimeFormatter);
            String author = "xxx";
            File changeLog = svnManager.getChangeLog(start, end, author);
            svnManager.staticticsCodeAdd(changeLog);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void testChangeFileList(){
        try {
            svnManager.getChangeFileList(105253L);
        } catch (SVNException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void testCheckoutFileToString(){
        try {
            // 该路径为相对项目目录的路径
            String filePath = "xxx";
            String content = svnManager.checkoutFileToString(filePath);
            System.out.println(content);
        }catch (SVNException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void testCheckPath(){
        // 该路径为相对项目目录的路径
        String fileOrFolderPath = "xxx";
        int i = svnManager.checkPath(fileOrFolderPath);
        switch (i){
            case 1:
                System.out.println("存在");
                break;
            case 0:
                System.out.println("不存在");
                break;
            case -1:
                System.out.println("出错");
        }
    }

    @Test
    public void testListFolder(){
        try {
            // 该路径为相对项目目录的路径
            String folderPath = "xxx";
            List<SVNDirEntry> svnDirEntries = svnManager.listFolder(folderPath);
            svnDirEntries.forEach(entry-> System.out.println(entry.getName()));
        } catch (SVNException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @After
    public void close() {
        svnManager.closeSession();
    }

}

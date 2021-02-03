package com.example.svnkit;

import org.joda.time.LocalDateTime;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 该件提供 SVN 管理及相关应用的功能
 *
 * @author dengzm
 */
public class SVNManager {

    boolean readonly = true;
    private final String tempDir = System.getProperty("java.io.tmpdir");
    private final DefaultSVNOptions options = SVNWCUtil.createDefaultOptions(readonly);
    private final Random random = new Random();
    private SVNDiffClient diffClient;
    private SVNRepository repository;
    private ISVNAuthenticationManager authManager;
    private String url = null;
    private SVNLogClient logClient;
    private SVNURL rootUrl;
    private SVNURL projectUrl;

    /**
     * 连接到svn存储库
     *
     * @param url      存储库地址
     * @param username 用户名
     * @param password 密码
     */
    public void createSession(String url, String username, String password) throws SVNException {
        // 设定svn存储库地址
        repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
        options.setDiffCommand("-x -w");
        // 设定登录svn存储库的用户名和密码
        authManager = SVNWCUtil.createDefaultAuthenticationManager(username, password.toCharArray());
        repository.setAuthenticationManager(authManager);
        // 尝试连接svn,测试是否可以正常连接
        repository.testConnection();

        diffClient = new SVNDiffClient(authManager, options);
        diffClient.setGitDiffFormat(true);

        logClient = new SVNLogClient(authManager, options);

        rootUrl = repository.getRepositoryRoot(false);
        projectUrl = SVNURL.parseURIEncoded(url);
        this.url = url;
    }

    /**
     * 根据起始结束时间及操作人用户名查询提交记录
     *
     * @param beginDateTime 开始时间
     * @param endDateTime   结束时间
     * @return 提交记录对象集合
     * @throws SVNException 异常
     */
    public List<SVNLogEntry> getLogs(LocalDateTime beginDateTime, LocalDateTime endDateTime, String author) throws SVNException {
        return getLogs(beginDateTime, endDateTime).stream()
                .filter(log -> author == null || log.getAuthor().equalsIgnoreCase(author))
                .sorted(Comparator.comparing(SVNLogEntry::getDate))
                .collect(Collectors.toList());
    }

    /**
     * 据起始结束日查询提交记录
     *
     * @param beginDateTime 开始时间
     * @param endDateTime   结束时间
     * @return 提交记录对象集合
     * @throws SVNException 异常
     */
    public List<SVNLogEntry> getLogs(LocalDateTime beginDateTime, LocalDateTime endDateTime) throws SVNException {
        long startVersion = repository.getDatedRevision(beginDateTime.toDate());
        long endVersion = repository.getDatedRevision(endDateTime.toDate()) + 1;
        return getLogs(startVersion, endVersion);
    }

    /**
     * 据起始结束日查询提交记录
     *
     * @param startVersion 开始版本号
     * @param endVersion   结束版本号
     * @return 提交记录对象集合
     * @throws SVNException 异常
     */
    @SuppressWarnings("unchecked")
    public List<SVNLogEntry> getLogs(long startVersion, long endVersion) throws SVNException {
        return (List<SVNLogEntry>) repository.log(new String[]{""}, null, startVersion, endVersion, true, true);
    }

    /**
     * 根据开始结束日期及用户名获取比较日志,并存入临时文件
     *
     * @param beginDateTime 开始时间
     * @param endDateTime   结束时间
     * @param author        用户名
     * @return 比较日志文件对象
     * @throws SVNException 异常
     */
    public File getChangeLog(LocalDateTime beginDateTime, LocalDateTime endDateTime, String author) throws SVNException {
        long startVersion = repository.getDatedRevision(beginDateTime.toDate());
        long endVersion = repository.getDatedRevision(endDateTime.toDate()) + 1;
        List<SVNLogEntry> logs = getLogs(startVersion, endVersion);
        List<String> filePathList = logs.parallelStream()
                .filter(item -> author == null || item.getAuthor().equalsIgnoreCase(author))
                .map(log -> log.getChangedPaths().keySet().toArray(new String[0]))
                .flatMap(Arrays::stream)
                .map(key -> rootUrl + key)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        return getChangeLog(startVersion, endVersion, filePathList);
    }

    /**
     * 根据起始结束版本号获取指定文件集合的版本比较日志,并存入临时文件
     *
     * @param startVersion 开始版本号
     * @param endVersion   结束版本号
     * @param filePathList 文件列表(svn中完整文件名)
     * @return 比较日志文件对象
     */
    public File getChangeLog(long startVersion, long endVersion, List<String> filePathList) {
        File tempLogFile = null;
        String svnDiffFile;
        try {
            do {
                svnDiffFile = tempDir + "/svn_diff_file_" + startVersion + "_" + endVersion + "_" + random.nextInt(10000) + ".txt";
                tempLogFile = new File(svnDiffFile);
            } while (tempLogFile.exists());
            tempLogFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (OutputStream os = new FileOutputStream(tempLogFile)) {
            for (String filePath : filePathList) {
                byte[] bytes;
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    diffClient.doDiff(SVNURL.parseURIEncoded(filePath),
                            SVNRevision.create(startVersion),
                            SVNURL.parseURIEncoded(filePath),
                            SVNRevision.create(endVersion),
                            SVNDepth.UNKNOWN, true, bos);
                    bytes = bos.toByteArray();
                }
                os.write(bytes);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tempLogFile;
    }

    /**
     * 分析版本比较日志文件，统计代码增量
     *
     * @param file 变更日志文件
     * @return 代码增量
     * @throws Exception 异常
     */
    public int staticticsCodeAdd(File file) throws Exception {
        System.out.println("开始统计代码的变更类型及变更行数");
        int sum = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            StringBuffer buffer = new StringBuffer(1024);
            boolean start = false;
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("Index:")) {
                    if (start) {
                        ChangeFile changeFile = parseChangeFile(buffer);
                        int oneSize = countAddLine(changeFile.getFileContent());
                        System.out.println("filePath=" + changeFile.getFilePath() + "  changeType=" + changeFile.getChangeType() + "  addLines=" + oneSize);
                        sum += oneSize;
                        buffer.setLength(0);
                    }
                    start = true;
                }
                buffer.append(line).append('\n');
            }
            // 统计最后一个文件的代码增量
            if (buffer.length() > 0) {
                ChangeFile changeFile = parseChangeFile(buffer);
                int oneSize = countAddLine(changeFile.getFileContent());
                System.out.println("filePath=" + changeFile.getFilePath() + "  changeType=" + changeFile.getChangeType() + "  addLines=" + oneSize);
                sum += oneSize;
            }
        }
        boolean deleteFile = file.delete();
        System.out.println("统计结束，删除版本计较日志文件：" + (deleteFile ? "成功" : "失败"));
        return sum;
    }

    /**
     * 解析单个文件变更日志
     *
     * @param str 变更文件对象的内容
     * @return 单个文件对象
     */
    public ChangeFile parseChangeFile(StringBuffer str) {
        int index = str.indexOf("\n@@");
        if (index > 0) {
            String header = str.substring(0, index);
            String[] headers = header.split("\n");
            String filePath = headers[0].substring(7);
            char changeType = 'U';
            boolean oldExist = !headers[2].endsWith("(nonexistent)");
            boolean newExist = !headers[3].endsWith("(nonexistent)");
            if (oldExist && !newExist) {
                changeType = 'D';
            } else if (!oldExist && newExist) {
                changeType = 'A';
            } else if (oldExist) {
                changeType = 'M';
            }
            int bodyIndex = str.indexOf("@@\n") + 3;
            String body = str.substring(bodyIndex);
            return new ChangeFile(filePath, changeType, body);
        } else {
            String[] headers = str.toString().split("\n");
            String filePath = headers[0].substring(7);
            return new ChangeFile(filePath, 'U', null);
        }
    }

    /**
     * 通过比较日志，统计以+号开头的非空行
     *
     * @param content 变更文件内容
     * @return +号开头的非空行的数量
     */
    public int countAddLine(String content) {
        int sum = 0;
        if (content != null) {
            content = '\n' + content + '\n';
            char[] chars = content.toCharArray();
            int len = chars.length;
            //判断当前行是否以+号开头
            boolean startPlus = false;
            //判断当前行，是否为空行（忽略第一个字符为加号）
            boolean notSpace = false;
            for (int i = 0; i < len; i++) {
                char ch = chars[i];
                if (ch == '\n') {
                    //当当前行是+号开头，同时其它字符都不为空，则行数+1
                    if (startPlus && notSpace) {
                        sum++;
                        notSpace = false;
                    }
                    //为下一行做准备，判断下一行是否以+头
                    if (i < len - 1 && chars[i + 1] == '+') {
                        startPlus = true;
                        //跳过下一个字符判断，因为已经判断了
                        i++;
                    } else {
                        startPlus = false;
                    }
                } else if (startPlus && ch > ' ') { //如果当前行以+开头才进行非空行判断
                    notSpace = true;
                }
            }
        }
        return sum;
    }

    /**
     * 统计一段时间内代码增加量
     *
     * @param beginDateTime 开始时间
     * @param endDateTime 结束时间
     * @return 代码增加量
     * @throws Exception 异常
     */
    public int staticticsCodeAddByTime(LocalDateTime beginDateTime, LocalDateTime endDateTime) throws Exception {
        int sum = 0;
        List<SVNLogEntry> entryList = getLogs(beginDateTime, endDateTime);
        if (entryList.size() > 0) {
            long lastVersion = entryList.get(0).getRevision();
            for (SVNLogEntry log : entryList) {
                File logFile = getChangeLog(lastVersion, log.getRevision(), new ArrayList<>());
                int addSize = staticticsCodeAdd(logFile);
                sum += addSize;
                lastVersion = log.getRevision();
            }
        }
        return sum;
    }

    public List<SVNLogEntryPath> getChangeFileList(long version) throws SVNException {
        List<SVNLogEntryPath> result = new ArrayList<>();
        String[] paths = {"."};
        SVNRevision pegRevision = SVNRevision.create(version);
        SVNRevision startRevision = SVNRevision.create(version);
        SVNRevision endRevision = SVNRevision.create(version);
        boolean stopOnCopy = false;
        boolean discoverChangedPaths = true;
        long limit = 9999L;
        ISVNLogEntryHandler handler = logEntry -> {
            System.out.println("Author: " + logEntry.getAuthor());
            System.out.println("Date: " + logEntry.getDate());
            System.out.println("Message: " + logEntry.getMessage());
            System.out.println("Revision: " + logEntry.getRevision());
            System.out.println("-------------------------");
            Map<String, SVNLogEntryPath> maps = logEntry.getChangedPaths();
            Set<Map.Entry<String, SVNLogEntryPath>> entries = maps.entrySet();
            for (Map.Entry<String, SVNLogEntryPath> entry : entries) {
                SVNLogEntryPath entryPath = entry.getValue();
                result.add(entryPath);
                System.out.println(map.get(entryPath.getType()) + " " + entryPath.getPath());
            }
        };

        try {
            logClient.doLog(projectUrl, paths, pegRevision, startRevision, endRevision, stopOnCopy, discoverChangedPaths, limit, handler);
        } catch (SVNException e) {
            System.out.println("Error in doLog() ");
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 获取指定文件最新内容
     *
     * @param filePath 文件全路径
     * @return 文件内容
     */
    public String checkoutFileToString(String filePath) throws SVNException {
        SVNDirEntry entry = repository.getDir(filePath, -1, false, null);
        int size = (int) entry.getSize();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(size);
        SVNProperties properties = new SVNProperties();
        repository.getFile(filePath, -1, properties, outputStream);
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * 列出指定SVN目录下的子目录或文件
     *
     * @param folderPath svn目录
     * @return 子目录或文件
     */
    public List<SVNDirEntry> listFolder(String folderPath) throws SVNException {
        if (checkPath(folderPath) == 1) {
            Collection<SVNDirEntry> list = repository.getDir(folderPath, -1, null, (List<SVNDirEntry>) null);
            List<SVNDirEntry> dirs = new ArrayList<>(list.size());
            dirs.addAll(list);
            return dirs;
        }
        return new ArrayList<>();
    }

    /**
     * 检查路径是否存在
     *
     * @param path svn路径
     * @return 1：存在    0：不存在   -1：出错
     */
    public int checkPath(String path) {
        SVNNodeKind nodeKind;
        try {
            nodeKind = repository.checkPath(path, -1);
            boolean result = nodeKind != SVNNodeKind.NONE;
            if (result) {
                return 1;
            }
        } catch (SVNException e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    /**
     * 关闭连接
     */
    public void closeSession() {
        repository.closeSession();
    }

    Map<Character, String> map = new HashMap<>() {{
        put('A', "添加(A)");
        put('M', "修改(M)");
        put('D', "删除(D)");
        put('R', "删除(R)");
        put('U', "删除(U)");
    }};

}

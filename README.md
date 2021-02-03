# 前言
在工作中，由于项目新需求在不同的分支上同时进行开发，导致在合并代码时对自己涉及到的部分得非常小心地合并。这就需要你知道整个功能开发完，你涉及到哪些文件的哪些改动。
另外，我们项目上线前都必须对所有上线包进行包对比，每个开发对自己涉及部分进行检查，并每次轮一人对所有上线包进行包对比。最近一次上线，轮到我来对所有上线包进行包对比，
花了一天做这个事，感觉人工对比效率太低且质量不高，在通过查阅资料，了解到了SVNKit 这个工具，就打算用SVNKit来提高一下包对比的效率。

# 介绍
SVNKit (JavaSVN) 是一个纯 Java 的 SVN 客户端库。通过以Jar包形式，可以很好的引入到你的java工程中。   
SVNKIT官网：https://svnkit.com/

# 引入
  在该项目中用到的依赖   
    <dependency>   
      <groupId>org.tmatesoft.svnkit</groupId>   
      <artifactId>svnkit</artifactId>   
      <version>1.10.1</version>   
    </dependency>   
    <!--提供日期相关功能(比java8的日期api更友好)-->   
    <dependency>   
      <groupId>joda-time</groupId>   
      <artifactId>joda-time</artifactId>   
      <version>2.10.6</version>   
    </dependency>   
    <!--用于编写测试类-->   
    <dependency>   
      <groupId>junit</groupId>   
      <artifactId>junit</artifactId>   
      <version>4.10</version>   
      <scope>test</scope>   
    </dependency>   

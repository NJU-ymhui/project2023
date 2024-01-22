*推荐使用IDEA*

[主要类](#mainClass)

    [Packet.java](#Packet)

    [Data.java](#Data)

    [Transformer.java](#Transformer)

    [Window.java](#Window)

    [Error.java](#Error)

    [Controller.java](#Controller)

    [Client.java](#Client)

    [Server.java](#Server)

[协议实现](#protocol)

[功能实现](#func)

    [使用类似TCP的handshake机制建立和断开连接](#f1)

    [单工，连接分发送方和接收方，接收方除必要的控制报文外不应发送数据报文，发送的数据应是字节流而非字符流](#f2)

    [需能配置STP报文的最大分段大小](#f3)

    [传输按固定窗口方式进行](#f4)

    [需模拟数据包丢失、乱序、超时、出错的情况（如根据概率随机“丢失”数据包）](#f5)

    [需模拟确认包丢失、乱序、超时的情况](#f6)

    [文件存储](#f7)

    [可以自行配置服务端socket（IP， port）， 可以选择客户端port](#f8)

    [可以回显传输中的报文，并且服务端以十六进制字节形式输出（恒回显），客户端以可视化报文形式输出（可选）](#f9)

    [用户可与client交互](#f10)

[运行方式](#how)

    [客户端](#howc)

    [服务端](#hows)

[交互指令](#instruction)

[使用示例](#example)

## <span id="mainClass">主要类</span>

### <span id="Packet">Packet.java</span>

    private Data data = null; //数据部分 
    public boolean isAck = false; //是否应答
    //报文结构
    private byte[] srcPort = new byte[2];private byte[] destPort = new byte[2];
    private byte[] id = new byte[4];//序号字段
    private byte[] ack = new byte[4];//应答号字段
    private byte[] spcBytes = new byte[2];private byte[] Window = new byte[2];//spc[0]控制标记,spc[1]标记位如SYN,ACK
    private byte[] check = new byte[2];private byte[] urgent = new byte[2];
    private byte[] options;private byte[] align;//填充
    public int MSS;//Packet转字节时会在首部固定20字节后加入4字节的MSS，相当于options存放MSS
    //end
    public byte[] getBytes();//Packet->bytes
    public void setMessage(int msg);//设置报文控制标记
    //...
    //对应的set与get方法
    //...
    public String toString();

### <span id="Data">Data.java</span>

    private byte[] data;
    //set&get...
    public void setData(byte[] data);
    public byte[] getData();
    public String toString();

### <span id="Transformer">Transformer.java</span>

静态辅助类，负责整数->不同字节位数的字节数组，字节数组组合->整数

    public static int toInteger(byte[] bytes);
    public static byte[] toBytes(int number, int bytes);

### <span id="Window">Window.java</span>

    public final int DEFAULT_SIZE = 100;
    public final int DEFAULT_SEGMENT = 20;
    private int size = DEFAULT_SIZE; // Byte
    private int segmentSize = DEFAULT_SEGMENT; // 每个数据段数据的大小，就是MSS的值
    public Packet[] packets;
    public void initPackets();//初始化Window报文
    //...
    //set&get方法
    //...
    public boolean ifFinished();//检查window数据包是否发送完毕
    public boolean full();//窗口是否满
    public void replace(Packet packet);//装填报文

### <span id="Error">Error.java</span>

静态辅助类，用于检错

    //利用spcByte[0]进行标记从而模拟
    //类似故障字：0b00000000正常，0b00000001出错, 0b00000010超时, 0b00000100丢失, 0b00001000乱序
    //至多出现一种问题
    private final static int ALL_KINDS_MSG;//各类标记...
    public static int check(Packet rcvPacket);//判断是否出错/出错类型

### <span id="Controller">Controller.java</span>

静态辅助类，用于进行报文控制，主要针对控制报文的构造与识别

    private final static int ALL_KINDS_MSG;//各类标记...
    public static Packet getCtrlPkt(String path);//获得设置路径的控制Packet
    public static Packet getCtrlPkt(int mssOrWindowSize, String choice);//获得其它类型的控制报文
    //设置服务端客户端希望接受文件的路径
    public static void setPath(Client client, Packet ctrl, Socket socket) throws Exception;
    public static int check(Packet packet);//检查当前报文类型
    public static void printHelp();//打印帮助
    public static void printHex(Packet p);//打印十六进制形式报文

### <span id="Client">Client.java</span>

客户端代码

    public Packet receivedPacket = null;//收到的报文
    public Packet sentPacket = null;//上次发出去的报文
    public int MSS = 1500;
    public boolean screen = true; //是否回显，默认true
    public boolean fileRdy = false;//server的文件是否就绪
    protected int port;//自己端口
    protected Window window = new Window(); //创建一个窗口，维护这个window
    protected ServerSocket welcomeSocket;
    protected Socket connectionSocket = null;//建立起的链接
    private byte[] serverAddr = new byte[4];//服务器地址
    private int serverPort;//服务器端口
    private int serverWindowSize = 100;//缓存服务端的窗口大小
    
    protected void receive(Packet packet);//接收报文
    protected byte[] getBytes(InputStream stream) throws Exception;//从流里读字节直到读到终止符
    //...
    //对报文的check方法
    //...
    protected void send(Socket connectionSocket, Packet packet) throws IOException;//发送报文
    protected void print();//回显
    protected Packet buildPacket(byte[] fromClient);//将字节恢复为报文
    protected static void dataTransfer(Client host, Socket connectionSocket, String path) throws Exception;//传输
    protected void errorMsg(int chc) throws Exception;//错误确认
    public void reply(Client client, Packet srcPacket, Socket connectionSocket) throws Exception;//应答
    private void buildConnection(Socket target) throws Exception;//三次握手建立连接（纯握手，与后续交流独立开）
    private static void releaseConnection(Client client, Socket connectingSocket) throws Exception;//四次挥手释放
    private static void checkAndRenew(Packet packet, Window window);//根据应答报文更新窗口，主要是对应答报文确认
    private static void setWindow(Client client, Socket socket, int window_size) throws Exception;
    private static void setMSS(Client client, Socket socket, int mss) throws Exception;
    private static void getData(Client client, Socket socket, String s) throws Exception;//从服务端获得数据的逻辑控制
    private void check(Socket socket, Client client) throws Exception;//check指令
    
    main();

### <span id="Server">Server.java extends Client.java</span>

服务端代码

    private boolean up = false; //标记服务端是否已启用
    private boolean connected = false;//是否已连接
    private InetAddress ip = null;//服务器IP
    public String path;//待传输文件路径
    //其余共同变量/方法继承于Client.java
    
    public void setWindowSize();//根据当前报文中window值设置window大小
    public void start() throws Exception;//启用服务器
    public void close() throws Exception;//关闭服务器
    public void release() throws Exception;//退出后释放连接
    public boolean connect();//是否连接
    public boolean check();//对服务器状态作检查
    public void listen(int port);//监听某个端口
    public void print();//回显
    public Packet set(Packet ctrl) throws Exception;//根据控制报文更新状态
    private static void buildConnection(Server server) throws Exception;//三次握手（纯握手）
    private static void releaseConnection(Server server, Socket connectingSocket) throws Exception;//四次挥手
    
    main();

## <span id="protocol">协议实现</span>

类似于TCP的可靠传输，使用三次握手四次挥手建立和释放连接，并采用报文作为双方交流的基本单位，报文承载控制和数据。

## <span id="func">功能实现</span>

**<span id="f1">1. 使用类似TCP的handshake机制建立和断开连接</span>**

三次握手：

    Client.java
    private void buildConnection(Socket target);
    Server.java
    private static void buildConnection(Server server);

四次挥手：

    Client.java
    private static void releaseConnection(Client client, Socket connectingSocket);
    Server.java
    private static void releaseConnection(Server server, Socket connectionSocket);

**<span id="f2">2. 单工，连接分发送方和接收方，接收方除必要的控制报文外不应发送数据报文，发送的数据应是字节流而非字符流</span>**

连接双方为client和server，其中client（Client.java）作为接收方，server（Server.java）作为发送方，接收方可以发送控制报文（控制辅助类Controller.java），server发送数据报文（dataTransfer），通过getBytes方法可以转换为发送的字节流或接收字节流。

    Controller.java
    public final static int MSG; //各类控制信号
    public static void getCtrlPkt(param...); //获取控制报文
    //其余辅助函数
    Client.java
    protected static void dataTransfer(Client host, Socket connectionSocket, String path) throws Exception; //Server使用，Server继承Client
    public static void reply(Client client, Packet srcPacket, Socket connectingSocket); //Client使用，应答报文
    protected void errorMsg(int chc); //对于差错情况的确认与报告
    protected byte[] getBytes(InputStream stream) throws Exception; //从流里读字节直到读到终止符
    Packet.java
    public byte[] getBytes(); //将报文转换为字节流，并打上终止符"\0~~~~"
    //考虑到InputStream的read()方法会一直阻塞，所以必须要以终止符提示传输的结束，经过计算,在传输中出现"\0~~~~"的概率几乎为0
    //因此以"\0~~~~"作为终止符

**<span id="f3">3. 需能配置STP报文的最大分段大小（MSS）</span>**

用户通过与client进行交互配置MSS，Client.java内通过相应的控制报文实现。

    Client.java
    private static void setMSS(Client client, Socket socket, int mss) throws Exception;
    //通过辅助类Controller.java获取相应的控制报文进行控制(控制信号会指示从该报文学习新的MSS)

**<span id="f4">4. 传输按固定窗口方式进行，只有当窗口中每个数据包都确认传达，才可发送下一个窗口的数据包，最大窗口大小MWS需能配置，可逐包确认（不必如TCP中只确认连续数据包的最后一个可用数据包）</span>**

辅助类Window.java实现了固定窗口机制，通过Client.java中的dataTransfer加以调用并控制；用户通过与client进行交互配置window-size，Client.java内通过相应的控制报文实现；Client.java中的reply方法实现逐包确认。

    Window.java
    //内含属性值
    public boolean ifFinished(); //判断窗口是否可以装填新一轮的报文
    public boolean full(); //判断窗口是否已被报文填满
    public void replace(Packet packet); //将packet装入window
    Client.java
    protected static void dataTransfer(Client host, Socket connectionSocket, String path) throws Exception {
        Window window1 = host.window;
        //...
        if (window1.ifFinished()) {
            Packet p;
            //...
            window1.replace(p)
        }
        while(发送时) {
            //...
            send(window1.packets[i]...);
        }
        //...
        checkAndRenew(窗口...)//更新状态
    }
    private static void setWindow(Client client, Socket socket, int window_size) throws Exception;
    //设置最大窗口大小MWS，通过辅助类Controller.java获取相应的控制报文进行控制（控制信号会指示需要从该报文学习新的MWS）
    public void reply(Client client, Packet srcPacket, Socket connectionSocket) throws Exception;
    //逐包确认（应答），对于传输正确的数据报文给予正确的应答
    private static void getData(Client client, Socket socket, String s) throws Exception;
    //client使用该方法来获得数据，其中包括了对正常报文的reply和对异常情况的确认

**<span id="f5">5. 需模拟数据包丢失、乱序、超时、出错的情况（如根据概率随机“丢失”数据包）</span>**

利用Packet.java中的特殊字节来标记数据包的异常情况从而模拟丢失、乱序、超时、出错，并在dataTransfer中根据概率随机设置异常情况，辅助类Error.java。

    Packet.java
    private byte[] spcBytes = new byte[2]; //spcBytes[0]标记
    public void setMessage(int msg); //根据msg在Error.java中的类型设置标记
    Error.java
    private final static int MSG; //各类标记
    public static int check(Packet rcvPacket); //检查报文标记类型，从而辅助确认异常

**<span id="f6">6. 需模拟确认包丢失、乱序、超时的情况</span>**

根据报文中的标记确认出错类型，在Client.java中的errorMsg方法会对异常情况进行确认；getData方法会对每次收到的数据报文进行查验，如果没有发生异常会用reply进行应答，否则调用errorMsg确认异常；dataTransfer方法已经对于异常情况的模拟做好了应对。

    Client.java
    protected void errorMsg(int chc) throws Exception; //确认异常
    private static void getData(Client client, Socket socket, String s) throws Exception {
        Packet rcv;
        //Error.check(rcv)
        if (正常) {
            //...
            reply(...);
        } else {
            //...
            errMsg(...);
        }
    }
    protected static void dataTransfer(Client host, Socket connectionSocket, String path) throws Exception {
        //...
        if(异常情况发生) {
            packet.setMessage(异常标记);
            send(异常Packet);
            阻塞
            receive重传信号
            结束阻塞
        }
        send(正常data...)
        //...
    }

**<span id="f7">7. 文件存储</span>**

从服务端获得的文件可以存储在客户端的文件中，该文件由程序新建，并且可排除异常情况的干扰，确保文件传输与存储的正确性，主要在Client.java中的getData方法中实现。

    private static void getData(Client client, Socket socket, String s) throws Exception; //文件存储于客户端

**<span id="f8">8. 可以自行配置服务端socket（IP， port）， 可以选择客户端port</span>**

通过指令交互，Client.java与Server.java中主要通过main函数与构造函数实现

    Client.java
    public Client(String addr, String serverPort, String port); //addr连接的ip地址
    Server.java
    main()//内含初始化配置控制

**<span id="f9">9. 可以回显传输中的报文，并且服务端以十六进制字节形式（原始形式）输出（恒回显），客户端以可视化报文形式输出（可选）</span>**

通过Packet.java中的getBytes方法可以获得报文的字节形式，从而输出字节形式并有相应的回显辅助函数；可视化报文可通过Packet.java的toString方法辅助实现。

    Packet.java
    public byte[] getBytes(); //获取字节流
    public String toString(); //获取报文的字符串形式
    Controller.java
    public static void print(Packet p); //打印16进制字节格式的报文（toHexString)

**<span id="f10">10. 用户可与client交互</span>**

详见交互指令

## <span id="how">运行方式</span>

*可分为客户端与服务端，使用终端运行*

### <span id="howc">客户端</span>

终端进入目录/project/Client/out/production/Client下

    java Client [port] //[port] 为客户端端口号，介于（0， 65535）之间，可能会由于权限问题部分端口号不可用
    例如
    java Client 100

### <span id="hows">服务端</span>

终端进入目录/project/Server/out/production/Server下

*注：当前服务端持有文件hello.c testfile，可以在/project/Server/out/production/Server内自行添加*

    java Server [ip] [port] //[ip]为服务端地址，[port]为端口号，可能由于权限问题ip只有以127开头的可用
    例如
    java Server 127.2.2.3 120

## <span id="instruction">交互指令</span>

与客户端交互

    hostname [usr-name]: 设置用户名
    set: 
        -w [size]: 设置窗口大小
        -m [mss]: 设置MSS
    get [filename]: 从服务端获取文件//当前服务端持有文件hello.c testfile
    display: 回显报文
    close: 不回显报文
    check: 查看当前window&mss
    quit / exit: 断开连接并退出客户端

## <span id="example">使用示例</span>

在/project/Server/out/production/Server下

*注：当前服务端持有文件hello.c testfile，可以在/project/Server/out/production/Server内自行添加*

    java Server 127.0.0.1 120
    //之后可以不管服务器了，退出用ctrl+c

在/project/Client/out/production/Client下

    java Client 100
    //根据地一个提示
    127.0.0.1
    //根据第二个提示
    120
    //若连接超时（服务器未匹配上会在0~15s内输出提示，等待时间受地址类型、电脑情况、权限等影响）
    //握手
    user> hostname nju
    nju> get hello.c
    nju> set -m 5
    nju> set -w 30
    //把mss设置的小一些可以传输更多的数据包，从而更可能出现异常情况
    nju> get testfile
    nju> close
    nju> get hello.c
    nju> display
    nju> get hello.c
    nju> quit

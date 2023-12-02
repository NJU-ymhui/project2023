# STPproject2023
## 功能
```
1.使用类似TCP的handshake机制建立和断开连接
2.单工，连接分发送方和接收方，接收方除必要的控制报文外不应发送数据报文，发送的数据应是字节流
  而非字符流
3.需能配置STP报文的最大分段大小（MSS）
4.传输按固定窗口方式进行，只有当窗口中每个数据包都确认传达，才可发送下一个窗口的数据包，
  最大窗口大小MWS需能配置，可逐包确认（不必如TCP中只确认连续数据包的最后一个可用数据包）
5.需模拟数据包丢失、乱序、超时、出错的情况（如根据概率随机“丢失”数据包）
6.需模拟确认包丢失、乱序、超时的情况
```
## 代码结构
```
project-
       |
       -Packet.java //模拟传输的数据报
       -Data.java //数据报中的data字段
       -Client.java //客户端
       -Server.java //服务端
       -Window.java //数据传输的窗口
```
## 最终界面, 命令行执行
> java Server port 运行服务端, port为监听的端口号
> Server开始监听某个端口
> <br>
> java Client 运行客户端，此时要判断是否与某个服务器建立连接，如没有建立连接，
> 要输出提示符提示与某个端口建立连接，引导用户输入端口号
> <br>
> 如果输入端口号与服务端监听的端口号一致，则尝试三次握手建立连接；否则该报文会传输超时
> <br>
> 例如：
> ```
> //运行服务端
> C:\>javac -encoding UTF-8 Server.java
> C:\>java Server 80 path MSS
> Server started! Listening on 80...
> 
> //运行客户端
> C:\>javac Client.java
> C:\>java Client 79
> Client started!
> Enter a port to connect:
> 1234
> Connection failed. Request time out...
> Enter a port to connect:
> 80
> //80端口是服务端监听的端口，开始三次握手建立连接，要在服务端与客户端回显相应的报文关键信息
> //三次握手逻辑应正确，否则连接失败
> //Client应回显一次Server的应答报文，Server应回显两次Client传来的的报文
> //如若无异常情况则应当连接成功，否则依然连接失败（如丢包或报文出错）
> Connection successful!
> Starting data transmission...

### 客户端需要实现的功能如下：
<li>连接服务器，通过指定的socket(IP, port)尝试与服务器建立连接（三次握手）</li>
<li>发送请求与控制帧，同时接受服务器的响应和数据</li>
<li>处理服务器的响应与数据，返回应答帧</li>
<li>请求数据</li>
<li>四次挥手断开连接</li>
<li>能够处理异常情况</li>

### 服务端要实现的功能如下：
<li>监听端口</li>
<li>接收并处理客户端发来的请求，体现为对其报文的处理</li>
<li>发送响应（对于握手与挥手）</li>
<li>传输数据</li>
<li>处理异常如超时重传</li>

## 计时器实现
1. 发送数据时设置计时器，超时重传（重传计时器）
<br>
判断方法：一段时间内没收到应答
<br>(使用java.util.concurrent包中的ExecutorService和Future)
<br>
超时逻辑实现：客户端随机对某个数据报不应答，使得服务端重传
2. 长时间没有数据往来询问是否保持连接（保持计时器）
<br>
询问方式：服务端发送一个空数据报文，ACK=1，如果对方应答则计时器置0重新计时，否则断开连接


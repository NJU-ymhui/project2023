# 测试
***先编译所有源文件***
## Server
- 启动后就不用管了
- java Server [port]
- 有的port用不了比如80

## Client
+ 启动 java Client [port]
+ 指令
```
set:
    set -w [number], 设置window大小，未设置时默认100
    set -m [number], 设置数据段大小，未设置时默认20
hostname:
    hostname [name], 设置用户名
get:
    get [filename], 在src里提供了两个测试文件 testfile hello.c, 可以自己加文件
quit: 退出
```
package mycode2;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    // GET test.html HTTP/1.0
    // GET test.jpg HTTP/1.0
    // GET test.xls HTTP/1.0

    ServerSocket server;
    private static final int PORT = 80;
    private static String root;
    ExecutorService executorService; // 线程池

    private static final int POOL_SIZE = 4; // 单个处理器线程池工作线程数目

    public Server() throws IOException {

        this.server = new ServerSocket(PORT, 5);
        this.executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * POOL_SIZE);
        // 创建线程池，根据可用处理器数目，设置线程数量
        System.out.println("服务器启动。");
    }

    public static void main(String[] args) throws Exception {
        if(args.length==1){
            System.out.println("根目录路径为： "+args[0]); //  web服务器根目录
            root = args[0];
        }else{
            System.out.println("未输入根目录路径。");
            return;
        }
        new Server().service(); // 启动服务
    }

    public void service(){
        Socket socket = null;
        //循环等待客户端连接
        while (true) {
            try {
                socket = server.accept(); // 等待并取出用户连接，并创建套接字
                System.out.println("新连接，连接地址：" + socket.getInetAddress() + "：" + socket.getPort()); // 客户端信息
                executorService.execute(new serverProcess(socket, root));
            } // 如果客户端断开连接，则应捕获该异常，但不中断整个while循环，使得服务器能继续与其他客户端通信
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}



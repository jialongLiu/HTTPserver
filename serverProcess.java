package mycode2;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class serverProcess implements Runnable {

    private static final int BUFFER_SIZE = 8192;
    private byte[] buffer;
    private final Socket socket;
    private final BufferedOutputStream bos;
    private final BufferedInputStream bis;
    private StringBuffer header;
    private File root;
    static private String CRLF = "\r\n";
    private String[] imgOrHtml = new String[6];
    private int contResource = 0;
    ByteArrayInputStream baseBufferIS;

    public serverProcess(Socket socket, String root) throws IOException {
        this.socket = socket;
        this.root = new File(root);
        buffer = new byte[BUFFER_SIZE];
        header = new StringBuffer();
        bis = new BufferedInputStream(socket.getInputStream());//获取输入流
        bos = new BufferedOutputStream(socket.getOutputStream()); //获取输出流
    }

    public void run() {

        try {

            processRequest();

            System.out.println("The request header:");
            System.out.println(header); //header包含客户端的请求信息

            processResponse();

            close();

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    //服务器端对请求信息进行相关处理
    public void processResponse() throws IOException {
        Scanner scanner = new Scanner(header.toString());
        String line;
        while ((line = scanner.next()) != null) {

            if (line.equals("GET")) {
                processGetResponse(scanner.next());
            } else if (line.equals("PUT")) {
                String filePath = scanner.next();
                File putFile = new File(root.getAbsolutePath() + filePath);

                String fileLength;
                while (true) {
                    fileLength = scanner.next();
                    if (fileLength.equals("Content-Length:")) {
                        fileLength = scanner.next();
                        break;
                    }
                }
                processPutResponse(putFile, Integer.parseInt(fileLength));
            } else {
                responseMessage( "501 Not Implemented", null);
            }
            break;
        }
    }

    public void processGetResponse(String filePath) throws IOException {
        File getFile = new File(root.getAbsolutePath() + "\\"+filePath);

        if (getFile.exists() && getFile.isFile()) {
            responseMessage("200 OK", getFile);
            FileInputStream fileStream = new FileInputStream(getFile);
            for (int i = 0; i < getFile.length() / BUFFER_SIZE; i++) {
                fileStream.read(buffer);
                bos.write(buffer);
            }
            fileStream.read(buffer);
            bos.write(buffer, 0, (int) getFile.length() % BUFFER_SIZE);

            // begin
            byte[] baseBuffer =checkImage(buffer);
            baseBufferIS = new ByteArrayInputStream(baseBuffer);
            splitSend(baseBuffer);
            // end

            // 最后一个包小一点，有多少读多少
            bos.flush();
            fileStream.close();
        } else {
            responseMessage("404 Not Found", null);
        }
    }

    // begin
    // 分隔并发送转换完的字符数组
    public void splitSend(byte[] baseBuffer) {
        // 借助ByteArrayInputStream分割字符数组
        byte[] newbuffer = new byte[BUFFER_SIZE];
        for (int i = 0; i < baseBuffer.length/ BUFFER_SIZE; i++) {
            baseBufferIS.read(newbuffer, 0, BUFFER_SIZE);
            bos.write(newbuffer);
        }
        baseBufferIS.read(newbuffer,0, BUFFER_SIZE);
        // 最后一个包小一点，有多少读多少
        bos.write(buffer, 0, (int) baseBuffer.length % BUFFER_SIZE);
    }

    // begin
    //判断是否有jpg如果有插入base64码
    public byte[] checkImage(byte[] byteArray){
        String content = new String(byteArray);// ljl
        Pattern pModel=Pattern.compile("(?<=\").*?(?=\")");
        Matcher mList=pModel.matcher(content);
        while(mList.find()){
            if(mList.group().endsWith("jpg")){
                // 用base64码替换原来的图片路径
                String base64Header = "data:image/jpeg;base64,";
                content = content.replace(mList.group(), base64Header+ImageToBase64(root + "\\"+mList.group()));
            }
        }
        return content.getBytes();
    }
    // end

    // begin
    // 将图片转换为base64码
    private static String ImageToBase64(String imgPath) {
        byte[] data = null;
        try (InputStream in = new FileInputStream(imgPath)){
            data = new byte[in.available()];
            in.read(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
       // 返回Base64编码过的字节数组字符串
       System.out.println("本地图片转换Base64: ");
       System.out.println(Base64.getEncoder().encodeToString(data));
       return Base64.getEncoder().encodeToString(data);
    }

    // end
    public void processPutResponse(File putFile, int fileLength) throws IOException {

        FileOutputStream fos = new FileOutputStream(putFile);
        for (int i = 0; i < fileLength / BUFFER_SIZE; i++) {
            bis.read(buffer);
            fos.write(buffer);
        }
        bis.read(buffer);
        fos.write(buffer, 0, fileLength % BUFFER_SIZE);
        responseMessage("201 Created", null);
        fos.close();

    }


    public void responseMessage(String statusCode, File file) throws IOException {

        String MIME = "text/html";
        long fileLength=0;
        if (file != null) {
            // 构造content-type的MIME
            if (file.getName().endsWith("htm") || file.getName().endsWith("html")) {
                MIME = "text/html";
            } else if (file.getName().endsWith(".jpg")) {
                MIME = "image/jpeg";
            } else if (file.getName().endsWith(".xls")) {
                MIME = "application/vnd.ms-excel";
            }	
            fileLength=file.length();
        }

        String htmlFile = "<html><body><center><h1>" + statusCode + "</h1></center></body></html>";
        if (statusCode.contains("200")) {
            htmlFile = "";
        }

        // 获取当前时间
        SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date date = new Date(System.currentTimeMillis());


        // 构造头部信息
        StringBuffer headerLine = new StringBuffer();
        headerLine.append("HTTP/1.0 ").append(statusCode).append(CRLF);
        headerLine.append("Date:").append(formatter.format(date)).append(CRLF);
        headerLine.append("Server: LJLServer/1.0").append(CRLF);
        headerLine.append("Content-type: ").append(MIME).append(CRLF);
        headerLine.append("Content-length: ").append(htmlFile.length() + fileLength).append(CRLF);
        headerLine.append(CRLF);
        headerLine.append(htmlFile);

        bos.write(headerLine.toString().getBytes());
        bos.flush();
    }




    public void processRequest() throws Exception {
        int last = 0, c = 0;
        /**
         * Process the header and add it to the header StringBuffer.
         * 对客户端请求进行解析，以报文最后有两个空格（CRLF = "\r\n";）为结束
         */
        boolean inHeader = true; // 对循环的控制
        while (inHeader && ((c = bis.read()) != -1)) {
            switch (c) {
                case '\r':
                    break;
                case '\n':
                    if (c == last) {
                        inHeader = false;
                        break;
                    }
                    last = c;
                    header.append("\n");
                    break;
                default:
                    last = c;
                    header.append((char) c);
            }
        }

    }

    public void close() throws Exception {
        this.socket.close();
        this.bis.close();
        this.bos.close();
    }
}

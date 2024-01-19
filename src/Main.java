import java.io.*;
import java.util.Random;

public class Main {
    public static void main(String[] args) throws Exception{
        System.out.println("Hello world!");
        RandomAccessFile file = new RandomAccessFile("testfile", "r");
        InputStream input = new FileInputStream(file.getFD());
        long p = file.getFilePointer();
        System.out.println("length: " + file.length());
        while (p < file.length()) {
            file.seek(p);
            System.out.println(input.read());
            p++;
        }
    }
}
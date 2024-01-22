import java.io.*;
import java.util.Random;

public class Main {
    public static void main(String[] args) throws Exception{
        int a1, a2, a3, a4;
        a1 = a2 = a3 = a4 = 0;
        for (int i = 0; i < 1000; i++) {
            int p = new Random().nextInt(1000);
            if (p < 100) {
                if (p < 25) a1++;
                else if (p < 50) a2++;
                else if (p < 75) a3++;
                else a4++;
            }
        }
        System.out.printf("0-24: %d\n25-49: %d\n50-74: %d\n75-99: %d\n", a1, a2, a3, a4);
    }
}
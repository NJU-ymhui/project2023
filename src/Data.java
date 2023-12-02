

public class Data {
    private byte[] data;
    public Data() {
        data = null;
    }
    public Data(byte[] data) {
        this.data = data;
    }
    public Data(String str) {
        this.data = str.getBytes();
    }
    public void setData(byte[] data) {
        this.data = data;
    }
    public byte[] getData() {
        return data;
    }
    @Override
    public String toString(){
        return new String(data);
    }
    // Test
    public static void main(String[] args) {
        Data data = new Data("Hello");
        System.out.println(data);
    }
}

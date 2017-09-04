class main {
    public static void main(String[] args) throws Exception {
        System.out.println(Class.forName("data").getDeclaredField("value").getInt(null));
    }
}

import java.sql.*;
public class TestJdbc {
  public static void main(String[] args) throws Exception {
    String[] urls = {
      "jdbc:sqlserver://localhost:1433;databaseName=cinebook_db;encrypt=true;trustServerCertificate=true",
      "jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=cinebook_db;encrypt=true;trustServerCertificate=true"
    };
    for (String url : urls) {
      try {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        Connection c = DriverManager.getConnection(url, "sa", "YourStrong@Passw0rd");
        System.out.println("OK: " + url);
        System.out.println(c.getMetaData().getDatabaseProductVersion());
        c.close();
      } catch (Exception e) {
        System.out.println("FAIL: " + url);
        e.printStackTrace(System.out);
      }
    }
  }
}

package zw.ac.uz.dpdms.alert;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailClient {
  public void send(String host, int port, String username, String password, boolean authenticate,
      boolean startTls, String from, String to, String subject, String text) throws IOException {
    if (host == null || host.isBlank()) throw new IllegalStateException("SMTP host is not configured");
    if (authenticate && (username == null || username.isBlank() || password == null || password.isBlank()))
      throw new IllegalStateException("SMTP credentials are not configured");
    if (authenticate && !startTls && port != 465) throw new IllegalStateException("SMTP authentication requires TLS");
    Socket socket = port == 465 ? SSLSocketFactory.getDefault().createSocket(host, port) : new Socket(host, port);
    socket.setSoTimeout(15000);
    try {
      BufferedReader in = reader(socket); BufferedWriter out = writer(socket);
      expect(in, 220); command(in, out, "EHLO dpdms.local", 250);
      if (startTls && !(socket instanceof SSLSocket)) {
        command(in, out, "STARTTLS", 220);
        socket = ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, host, port, true);
        ((SSLSocket) socket).startHandshake(); in = reader(socket); out = writer(socket);
        command(in, out, "EHLO dpdms.local", 250);
      }
      if (authenticate) {
        command(in, out, "AUTH LOGIN", 334);
        command(in, out, Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)), 334);
        command(in, out, Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)), 235);
      }
      command(in, out, "MAIL FROM:<" + clean(from) + ">", 250);
      command(in, out, "RCPT TO:<" + clean(to) + ">", 250, 251, 252);
      command(in, out, "DATA", 354);
      out.write("From: " + clean(from) + "\r\nTo: " + clean(to) + "\r\nSubject: " + clean(subject)
          + "\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: 8bit\r\n\r\n");
      for (String line : text.replace("\r", "").split("\n", -1)) out.write((line.startsWith(".") ? "." : "") + line + "\r\n");
      out.write(".\r\n"); out.flush(); expect(in, 250);
      command(in, out, "QUIT", 221);
    } finally { socket.close(); }
  }

  private BufferedReader reader(Socket socket) throws IOException { return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)); }
  private BufferedWriter writer(Socket socket) throws IOException { return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)); }
  private void command(BufferedReader in, BufferedWriter out, String command, int... expected) throws IOException {
    out.write(command + "\r\n"); out.flush(); expect(in, expected);
  }
  private void expect(BufferedReader in, int... expected) throws IOException {
    String line; int code = -1;
    do { line = in.readLine(); if (line == null || line.length() < 3) throw new IOException("Unexpected SMTP response");
      try { code = Integer.parseInt(line.substring(0, 3)); } catch (NumberFormatException e) { throw new IOException("Invalid SMTP response", e); }
    } while (line.length() > 3 && line.charAt(3) == '-');
    for (int value : expected) if (code == value) return;
    throw new IOException("SMTP rejected command with response " + line);
  }
  private String clean(String value) { return value == null ? "" : value.replace("\r", "").replace("\n", ""); }
}

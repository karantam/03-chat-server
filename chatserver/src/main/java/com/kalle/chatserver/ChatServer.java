package com.kalle.chatserver;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

/*
 * ChatServer class is the main class of the chatserver. It takes in the staring 
 * parameters and launches the srver and shuts it down when the user so requests.
 */
public class ChatServer {
    public static void main(String[] args) throws Exception {
        ChatDatabase.getInstance().open(args[0]);
        final ExecutorService pool;
        if (args.length == 3) {
            try {
                log("Launching Chatserver with args " + args[0] + args[1] + args[2]);
                HttpsServer server = HttpsServer.create(new InetSocketAddress(8001), 0);
                // configuring the server to use sslContext
                SSLContext sslContext = chatServerSSLContext(args[1], args[2]);
                server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                    @Override
                    public void configure(HttpsParameters params) {
                        InetSocketAddress remote = params.getClientAddress();
                        SSLContext c = getSSLContext();
                        SSLParameters sslparams = c.getDefaultSSLParameters();
                        params.setSSLParameters(sslparams);
                    }
                });
                // creating contexts
                ChatAuthenticator auth = new ChatAuthenticator();
                server.createContext("/registration", new RegistrationHandler(auth));
                HttpContext context = server.createContext("/chat", new ChatHandler());
                context.setAuthenticator(auth);
                // Creting thread pool
                pool = Executors.newCachedThreadPool();
                server.setExecutor(pool);
                log("Starting Chatserver!");
                server.start();
                Boolean running = true;
                while (Boolean.TRUE.equals(running)) {
                    String shutdown = System.console().readLine();
                    if (shutdown.equals("/quit")) {
                        running = false;
                    }else{
                        log("Type /quit to shut down the chatserver");
                    }
                }
                log("Shutting down Chatserver...");
                server.stop(3);
                ChatDatabase.getInstance().closeDB();
                log("Chatserver has been shutdown.");
            } catch (FileNotFoundException e) {
                // Certificate file not found!
                log("Certificate not found!");
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            log("Usage: java -jar chat-server-jar-file ../database.db ../keystore.jks password");
            log("Where the  first parameter is the database file with path,");
            log("the second parameter is the server's certificate file with path and");
            log("the third parameter is the  certificate files password.");
        }

    }

    /*
     * SSlContext method connects to the given certificate and loads it
     */
    private static SSLContext chatServerSSLContext(String pathToKeystore, String keystorePassword)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException,
            UnrecoverableKeyException, KeyManagementException {
        // creating SSLContext function
        // Old pass word "G8daUFSd9fhs35y4shJUh5fsnu6ubrT"
        char[] passphrase = keystorePassword.toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        // Opening the certificate at given location
        ks.load(new FileInputStream(pathToKeystore), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        // Had to change TLS to TLSv1.2 otherwise program gets stuck
        SSLContext ssl = SSLContext.getInstance("TLSv1.2");
        ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return ssl;
    }

    /*
     * log method is the print method for all server printouts
     */
    public static void log(String message) {
        DateTimeFormatter logdtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        System.out.println(logdtf.format(LocalDateTime.now()) + " " + message);
    }
}
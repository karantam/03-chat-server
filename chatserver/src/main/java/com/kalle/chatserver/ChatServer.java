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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

/*
 ChatServer
 */
public class ChatServer {
    public static void main(String[] args) throws Exception {
        ChatDatabase database = ChatDatabase.getInstance("ChatServer.db");
        if (args.length == 1) {
            try {
                log("Launching Chatserver with args " + args[0]);
                HttpsServer server = HttpsServer.create(new InetSocketAddress(8001), 0);
                // configuring the server to use sslContext
                SSLContext sslContext = chatServerSSLContext(args[0]);
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
                server.setExecutor(null);
                log("Starting Chatserver!");
                server.start();
            } catch (FileNotFoundException e) {
                // Certificate file not found!
                log("Certificate not found!");
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            log("Usage: java -jar chat-server-jar-file ../keystore.jks");
            log("Where the parameter is the server's certificate file with path.");
        }

    }

    private static SSLContext chatServerSSLContext(String pathToKeystore)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException,
            UnrecoverableKeyException, KeyManagementException {
        // creating SSLContext function
        char[] passphrase = "G8daUFSd9fhs35y4shJUh5fsnu6ubrT".toCharArray();
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

    // Print method for all server printouts
    public static void log(String message) {
        DateTimeFormatter logdtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        System.out.println(logdtf.format(LocalDateTime.now()) + " " + message);
    }
}
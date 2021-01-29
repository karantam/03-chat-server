package com.kalle.chatserver;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.InetSocketAddress;
import java.security.KeyStore;

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
        try {
            System.out.println("Launching Chatserver...");
            HttpsServer server = HttpsServer.create(new InetSocketAddress(8001), 0);
            // configuring the server to use sslContext
            SSLContext sslContext = chatServerSSLContext();
            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
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
            System.out.println("Starting Chatserver!");
            server.start();
        } catch (FileNotFoundException e) {
            // Certificate file not found!
            System.out.println("Certificate not found!");
            e.printStackTrace();
            return;
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    private static SSLContext chatServerSSLContext() throws Exception {
        // creating SSLContext function
        char[] passphrase = "G8daUFSd9fhs35y4shJUh5fsnu6ubrT".toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream("keystore.jks"), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        // Had to change TLS to TLSv1.2 otherwise program gets stuck
        SSLContext ssl = SSLContext.getInstance("TLSv1.2");
        ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return ssl;
    }
}
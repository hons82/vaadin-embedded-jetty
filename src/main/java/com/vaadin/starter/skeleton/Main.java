package com.vaadin.starter.skeleton;

import com.vaadin.flow.server.InitParameters;
import com.vaadin.flow.server.startup.RouteRegistryInitializer;
import com.vaadin.flow.server.startup.ServletContextListeners;

import org.atmosphere.cpr.ApplicationConfig;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.ssl.X509;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.webapp.*;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.servlet.http.HttpUtils;

/**
 * Run {@link #main(String[])} to launch your app in Embedded Jetty.
 * @author mavi
 */
public final class Main {

    private static Server server1;
    private static Server server2;
    private static QueuedThreadPool commonServerThreadPool;
    private SslContextFactory.Server sslServerContextFactory;

    public static void main(@NotNull String[] args) throws Exception {
        start(args);
//        server.join();
    }

    public static void start(@NotNull String[] args) throws Exception {
        // change this to e.g. /foo to host your app on a different context root
        final String contextRoot = "/";

        // detect&enable production mode
        if (isProductionMode()) {
            // fixes https://github.com/mvysny/vaadin14-embedded-jetty/issues/1
            System.out.println("Production mode detected, enforcing");
            System.setProperty("vaadin.productionMode", "true");
        }

        final WebAppContext context1 = new WebAppContext();
        context1.setBaseResource(findWebRoot());
        context1.setContextPath(contextRoot);
        context1.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*\\.jar|.*/classes/.*");
        context1.setConfigurationDiscovered(true);
        context1.getServletContext().setExtendedListenerTypes(true);
        context1.setThrowUnavailableOnStartupException(true);
        context1.setAttribute(
            AnnotationConfiguration.SERVLET_CONTAINER_INITIALIZER_EXCLUSION_PATTERN,
            RouteRegistryInitializer.class.getName()
        );
        context1.addEventListener(new ServletContextListeners());

        ServletHolder holder1 = context1.addServlet(FirstVaadinServlet.class, "/*");
        holder1.setAsyncSupported(true);
        // Init-Parameter für Vaadin
        holder1.setInitParameter(InitParameters.SERVLET_PARAMETER_HEARTBEAT_INTERVAL	, "300");
        holder1.setInitParameter(InitParameters.SERVLET_PARAMETER_ENABLE_PNPM		, Boolean.toString(true));
        // Init-Parameter für Atmosphere
        holder1.setInitParameter(ApplicationConfig.BROADCASTER_ASYNC_WRITE_THREADPOOL_MAXSIZE, "-1");
        holder1.setInitParameter(ApplicationConfig.WEBSOCKET_IDLETIME, "300000");

        final WebAppContext context2 = new WebAppContext();
        context2.setBaseResource(findWebRoot());
        context2.setContextPath(contextRoot + "webui/");
        context2.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*\\.jar|.*/classes/.*");
        context2.setConfigurationDiscovered(true);
        context2.getServletContext().setExtendedListenerTypes(true);
        context2.setThrowUnavailableOnStartupException(true);
        context2.setAttribute(
            AnnotationConfiguration.SERVLET_CONTAINER_INITIALIZER_EXCLUSION_PATTERN,
            RouteRegistryInitializer.class.getName()
        );
        context2.addEventListener(new ServletContextListeners());

        ServletHolder holder2 = context2.addServlet(SecondVaadinServlet.class, "/*");
        holder2.setAsyncSupported(true);
        // Init-Parameter für Vaadin
        holder2.setInitParameter(InitParameters.SERVLET_PARAMETER_HEARTBEAT_INTERVAL	, "300");
        holder2.setInitParameter(InitParameters.SERVLET_PARAMETER_ENABLE_PNPM		, Boolean.toString(true));
        // Init-Parameter für Atmosphere
        holder2.setInitParameter(ApplicationConfig.BROADCASTER_ASYNC_WRITE_THREADPOOL_MAXSIZE, "-1");
        holder2.setInitParameter(ApplicationConfig.WEBSOCKET_IDLETIME, "300000");

        int port = 8080;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }
        server1 = new Server(port);
        server1.setHandler(context1);
        server1.start();

        System.out.println("\n\n=================================================\n\n" +
        "Please open http://localhost:" + port + contextRoot + " in your browser\n\n" +
        "If you see the 'Unable to determine mode of operation' exception, just kill me and run `mvn -C clean package`\n\n" +
        "=================================================\n\n");

        port++;
        server2 = new Server(getCommonServerThreadPool());
        server2.setHandler(context2);
        server2.start();

        System.out.println("\n\n=================================================\n\n" +
        "Please open http://localhost:" + port + contextRoot + " in your browser\n\n" +
        "If you see the 'Unable to determine mode of operation' exception, just kill me and run `mvn -C clean package`\n\n" +
        "=================================================\n\n");
    }

    public static void stop() throws Exception {
        server1.stop();
        server1 = null;
        server2.stop();
        server2 = null;
    }

    public synchronized static ThreadPool getCommonServerThreadPool() {
        if (commonServerThreadPool == null) {
            commonServerThreadPool = new QueuedThreadPool();
            commonServerThreadPool.setDaemon(true);
            try {
                commonServerThreadPool.start();
            }
            catch (Exception exc) {		// Dürfte nie vorkommen
                System.out.println(exc);
            }
        }
        return commonServerThreadPool;
    }

    private ServerConnector createSecureServerConnector(int port) {
        HttpConfiguration httpsConfig = createHttpConfiguration();
        httpsConfig.setSecureScheme("https");
        httpsConfig.setSecurePort(port);
        httpsConfig.addCustomizer(new ForwardedRequestCustomizer());
        httpsConfig.addCustomizer(new SecureRequestCustomizer(false) {
            @Override
            protected void customize(SSLEngine sslEngine, Request request) {
                super.customize(sslEngine, request);
                SSLSession sslSession = sslEngine.getSession();
                X509 x509 = (X509)sslSession.getValue(X509_CERT);
                if (x509 == null) {
                    Certificate[] certificates = sslSession.getLocalCertificates();
                    Certificate cert0 = certificates != null && certificates.length >= 1 ? certificates[0] : null;
                    if (cert0 instanceof X509Certificate) {
                        x509 = new X509(null, (X509Certificate)cert0);
                        sslSession.putValue(X509_CERT, x509);
                    }
                }
                String serverName;
                if (x509 != null && !x509.matches(serverName = request.getServerName())) {
                    System.out.println(
                        "Host certificate mismatch ServerName=" + serverName
                            + " Alias=" + x509.getAlias()
                            + " Hosts=" + x509.getHosts()
                            + " Wilds=" + x509.getWilds()
                    );
                }
            }
        });
        ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
        alpn.setDefaultProtocol(HttpVersion.HTTP_1_1.asString());

        SslConnectionFactory ssl = new SslConnectionFactory(getServerSslContextFactory(), alpn.getProtocol());

        HTTP2ServerConnectionFactory h2 = new HTTP2ServerConnectionFactory(httpsConfig);

        ServerConnector connector = new ServerConnector(server2, ssl, alpn, h2, new HttpConnectionFactory(httpsConfig));
        connector.setPort(port);
        return connector;
    }

    /** Erzeugt ein neues HttpConfiguration-Objekt. */
    private static HttpConfiguration createHttpConfiguration() {
        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setRequestHeaderSize(16384);
        return httpConfiguration;
    }

    private static boolean isProductionMode() {
        final String probe = "META-INF/maven/com.vaadin/flow-server-production-mode/pom.xml";
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader.getResource(probe) != null;
    }

    /** Liefert die SslContextFactory. */
    private SslContextFactory.Server getServerSslContextFactory() {
        if (sslServerContextFactory == null) {
//            CommonApp app = CommonApp.getInstance();
            sslServerContextFactory = new SslContextFactory.Server();
//            sslServerContextFactory.setKeyStore(app.getServerKeyStore());
//            sslServerContextFactory.setKeyStorePassword(new String(app.getKeyStorePrivateKeyPassword()));
//            sslServerContextFactory.setTrustStore(app.getTrustStore());
            sslServerContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
//            if (!app.isOldJettyCfg())
                sslServerContextFactory.addExcludeProtocols("TLSv1", "TLSv1.1");
            sslServerContextFactory.setProvider("Conscrypt");
        }
        return sslServerContextFactory;
    }

    @NotNull
    private static Resource findWebRoot() throws MalformedURLException {
        // don't look up directory as a resource, it's unreliable: https://github.com/eclipse/jetty.project/issues/4173#issuecomment-539769734
        // instead we'll look up the /webapp/ROOT and retrieve the parent folder from that.
        final URL f = Main.class.getResource("/webapp/ROOT");
        if (f == null) {
            throw new IllegalStateException("Invalid state: the resource /webapp/ROOT doesn't exist, has webapp been packaged in as a resource?");
        }
        final String url = f.toString();
        if (!url.endsWith("/ROOT")) {
            throw new RuntimeException("Parameter url: invalid value " + url + ": doesn't end with /ROOT");
        }
        System.err.println("/webapp/ROOT is " + f);

        // Resolve file to directory
        URL webRoot = new URL(url.substring(0, url.length() - 5));
        System.err.println("WebRoot is " + webRoot);
        return Resource.newResource(webRoot);
    }
}

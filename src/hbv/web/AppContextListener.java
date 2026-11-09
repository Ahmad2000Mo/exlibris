package hbv.web;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Wird beim Start/Stopp der Webanwendung aufgerufen: baut den Redis-Verbindungspool auf und ab.
 */
public class AppContextListener implements ServletContextListener {

    private static final int REDIS_POOL_SIZE = 50;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext ctx = event.getServletContext();
        String server = ctx.getInitParameter("redisserver");
        String password = ctx.getInitParameter("redispassword");
        int port = JedisAdapter.parsePort(ctx.getInitParameter("redisport"));
        JedisAdapter.init(server, port, password, REDIS_POOL_SIZE);
        ctx.log("ExLibris gestartet, Redis: " + server + ":" + port);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        JedisAdapter.destroy();
        event.getServletContext().log("ExLibris beendet");
    }
}

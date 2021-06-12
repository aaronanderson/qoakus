package com.github.aaronanderson.qoakus;

import static io.vertx.core.http.HttpHeaders.CACHE_CONTROL;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;

@ApplicationScoped
public class SPARouter {

    public void setupRouter(@Observes Router router) {
        
        router.route("/index.html").handler(r -> {
            r.next();
            r.response().putHeader(CACHE_CONTROL, "no-store, no-cache, no-transform, must-revalidate, max-age=0");
        });
       
        Handler<RoutingContext> indexHandler = ctx -> ctx.reroute("/index.html");
        router.route("/edit").handler(indexHandler);
        router.route("/search").handler(indexHandler);
        router.route("/view*").handler(indexHandler);
    }

}
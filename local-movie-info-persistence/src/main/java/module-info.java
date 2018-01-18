module local.movie.info.persistence {
    exports com.github.rahmnathan.localmovies.data;
    exports com.github.rahmnathan.localmovies.persistence;
    requires movie.info.api;
    requires spring.context;
    requires spring.beans;
    requires java.logging;
    requires movie.info.omdb;
    requires spring.data.commons;
}
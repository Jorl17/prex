package prex.client;

/**
 * Created by jorl17 on 02/06/16.
 */
public interface ConsumerWithExceptions<T> {
    void consume(T obj) throws Exception;
}

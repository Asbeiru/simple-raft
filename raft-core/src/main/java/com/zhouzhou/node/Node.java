package com.zhouzhou.node;


/**
 * Node.
 */
public interface Node {


    /**
     * Start node.
     */
    void start();


    /**
     * Stop node.
     *
     * @throws InterruptedException if interrupted
     */
    void stop() throws InterruptedException;

}

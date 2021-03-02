package com.github.netty.annotation;

import java.lang.annotation.*;

/**
 * Created by wangzihao on 2018/12/5/005.
 */
public class Protocol {
    /**
     * RPC method
     */
    @Target({ElementType.METHOD,ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface RpcMethod{
        String value() default "";
    }

    /**
     * RPC parameter note :(used on the client interface, not required on the server)
     */
    @Target({ElementType.PARAMETER,ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface RpcParam{
        String value() default "";
    }

    /**
     * RPC service note :(to use RPC, the interface or class can be configured with or without annotations, the default is the class name of the interface)
     */
    @Target({ElementType.TYPE,ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface RpcService{
        /**
         * Address of the interface
         * @return value
         */
        String value() default "";

        /**
         * service version
         * @return any str
         */
        String version() default "";

        /**
         * Timeout time (milliseconds)
         * @return timeout
         */
        int timeout() default DEFAULT_TIME_OUT;
        /**
         * Default timeout
         */
        int DEFAULT_TIME_OUT = 1000;
    }
}

package com.pawsql.mcp.service;

import com.pawsql.mcp.context.PawSQLAuthContext;
import com.pawsql.mcp.model.JwtTokenPayload;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * 认证服务接口
 * 提供统一的认证信息获取和管理方法
 */
public interface AuthenticationService {
    
    /**
     * 获取当前认证上下文
     * 优先从当前线程的ThreadLocal中获取，如果不存在则尝试从其他来源获取
     *
     * @return 当前认证上下文，如果未认证则返回null
     */
    PawSQLAuthContext getCurrentAuthContext();
    
    /**
     * 获取当前认证信息
     *
     * @return 当前认证信息，如果未认证则返回null
     */
    JwtTokenPayload getCurrentTokenPayload();
    
    /**
     * 设置当前线程的认证信息
     *
     * @param tokenPayload 认证信息
     */
    void setCurrentTokenPayload(JwtTokenPayload tokenPayload);
    
    /**
     * 清除当前线程的认证信息
     */
    void clearCurrentTokenPayload();
    
    /**
     * 使用指定的认证上下文执行操作
     *
     * @param authContext 认证上下文
     * @param runnable 要执行的操作
     */
    void runWithAuthContext(PawSQLAuthContext authContext, Runnable runnable);
    
    /**
     * 使用指定的认证上下文执行操作并返回结果
     *
     * @param authContext 认证上下文
     * @param supplier 要执行的操作
     * @param <T> 返回值类型
     * @return 操作的返回结果
     */
    <T> T supplyWithAuthContext(PawSQLAuthContext authContext, Supplier<T> supplier);
    
    /**
     * 使用指定的认证上下文执行操作并返回结果
     *
     * @param authContext 认证上下文
     * @param callable 要执行的操作
     * @param <V> 返回值类型
     * @return 操作的返回结果
     * @throws Exception 如果操作抛出异常
     */
    <V> V callWithAuthContext(PawSQLAuthContext authContext, Callable<V> callable) throws Exception;
    
    /**
     * 创建一个包装了认证上下文的Runnable
     * 在新线程中执行时，会自动使用指定的认证上下文
     *
     * @param authContext 认证上下文
     * @param runnable 要执行的任务
     * @return 包装后的Runnable
     */
    Runnable withAuthContext(PawSQLAuthContext authContext, Runnable runnable);
    
    /**
     * 创建一个包装了认证上下文的Callable
     * 在新线程中执行时，会自动使用指定的认证上下文
     *
     * @param authContext 认证上下文
     * @param callable 要执行的任务
     * @param <V> 返回值类型
     * @return 包装后的Callable
     */
    <V> Callable<V> withAuthContext(PawSQLAuthContext authContext, Callable<V> callable);
    
    /**
     * 创建一个包装了认证上下文的Supplier
     * 在新线程中执行时，会自动使用指定的认证上下文
     *
     * @param authContext 认证上下文
     * @param supplier 要执行的任务
     * @param <T> 返回值类型
     * @return 包装后的Supplier
     */
    <T> Supplier<T> withAuthContext(PawSQLAuthContext authContext, Supplier<T> supplier);
}

package com.zoee.equipops.common.context;

public class UserContext {
    private static final ThreadLocal<Long> USER_ID=new ThreadLocal<>();
    private static final ThreadLocal<Long> DEPT_ID=new ThreadLocal<>();
    public static void setUserId(Long userId){
        USER_ID.set(userId);
    }
    public static Long getUserId(){
        return USER_ID.get();
    }
    public static void setDeptId(Long deptId){
        DEPT_ID.set(deptId);
    }
    public static Long getDeptId(){
        return DEPT_ID.get();
    }
    public static void remove(){
        USER_ID.remove();
        DEPT_ID.remove();
    }
}

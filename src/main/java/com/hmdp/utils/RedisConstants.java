package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "loginCode:";
    public static final Integer LOGIN_CODE_TTL = 2;
    public static final String LOGIN_USER_TOKEN = "userToken:";
    public static final Integer LOGIN_USER_TTL = 30;

    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String CACHE_SHOP_LIST = "cache:shop:list";
    public static final Integer CACHE_SHOP_LIST_TTL = 30;

}

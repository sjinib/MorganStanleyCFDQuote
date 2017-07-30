/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.config;

/**
 *
 * @author Siteng Jin
 */
public class Configs {
    public static final String ACCOUNT = "ACCOUNT";
    public static final String ORDER_SIZE_DEFAULT = "ORDER_SIZE_DEFAULT";
    public static final String STATIC_OFFSET = "STATIC_OFFSET";
    public static final String DISTORTION_RATE = "DISTORTION_RATE";
    public static final String SOURCE_CONID = "SOURCE_CONID";
    public static final String SOURCE_EXCHANGE = "SOURCE_EXCHANGE";
    public static final String TRADE_CONID = "TRADE_CONID";
    public static final String TRADE_EXCHANGE = "TRADE_EXCHANGE";
    public static final String POSITION_ADJUSTMENT = "POSITION_ADJUSTMENT";
    
    public static String[] getAllConfigs(){
        return new String[]{ACCOUNT, ORDER_SIZE_DEFAULT, STATIC_OFFSET, DISTORTION_RATE, 
        SOURCE_CONID, SOURCE_EXCHANGE, TRADE_CONID, TRADE_EXCHANGE, POSITION_ADJUSTMENT};
    }
}

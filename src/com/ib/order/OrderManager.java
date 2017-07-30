/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.order;

import com.ib.api.IBClient;
import org.apache.log4j.Logger;
import java.util.HashMap;
import com.ib.client.Order;
import com.ib.client.Types;
import java.util.Iterator;
import com.ib.config.*;
import com.ib.quote.QuoteManager;
import com.ib.position.*;
import com.ib.client.Contract;
import com.ib.client.OrderType;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Siteng Jin
 */
public class OrderManager{
    private static final Logger LOG = Logger.getLogger(OrderManager.class);
    
    private static ConfigReader m_configReader = null;
    private static PositionManager m_positionManager = null;
    private static QuoteManager m_quoteManager = null;
    
    public static final Object OPENORDERLOCK = new Object();
    private static final Object ORDERACCESSLOCK = new Object();
    public static final Object CANCELORDERLOCK = new Object();
    
    private IBClient m_client = null; 
    private int tradeConid = Integer.MAX_VALUE;
    private String tradeExchange = null;
    private int orderSizeDefault = Integer.MAX_VALUE; // Though order.totalquantity is double, still read as int to avoid confusion
    private String account = null;
    private int positionAdjustment = Integer.MAX_VALUE;
    
    private HashMap<Integer, OrderInfo> m_orderMap = null;
    private int buyOrderId = Integer.MAX_VALUE;
    private int sellOrderId = Integer.MAX_VALUE;
    private List pendingCancelList = null;
    
    public OrderManager( IBClient client){
        m_client = client;
        if(m_orderMap == null){
            m_orderMap = new HashMap<Integer, OrderInfo>();
        }
        if(m_configReader == null){
            m_configReader = ConfigReader.getInstance();
        }
        if(m_positionManager == null){
            m_positionManager = m_client.getPositionManager();
        }
        if(m_quoteManager == null){
            m_quoteManager = m_client.getQuoteManager();
        }
        if(pendingCancelList == null){
            pendingCancelList = new ArrayList<Integer>();
        }
    }
    
    public void startTrade(){
        while(true){
            try{
                LOG.debug("------------Not trading in order manager----------");
                Thread.sleep(5000);
            } catch (Exception e){
                
            }
        }
    }
    
    public void requestOpenOrder(){
        m_client.getSocket().reqOpenOrders();
        LOG.debug("Sent reqOpenOrders()");
    }
    
    public void updateOpenOrder(int orderId , Order order){
        synchronized(ORDERACCESSLOCK){
            if(m_orderMap.containsKey(orderId)){
                OrderInfo o = m_orderMap.get(orderId);
                o.setOrder(order);
                m_orderMap.replace(orderId, o);
                LOG.debug("Updated open order. Order: " + m_orderMap.get(orderId).getOrder().action() + " " +
                        m_orderMap.get(orderId).getOrder().totalQuantity() + ", Filled: " + m_orderMap.get(orderId).getFilled());
            } else {
                m_orderMap.put(orderId, new OrderInfo(order, Double.MAX_VALUE, Double.MAX_VALUE));
                verifyOrders();
                if(order.action() == Types.Action.BUY){
                    buyOrderId = orderId;
                } else if(order.action() == Types.Action.SELL){
                    sellOrderId = orderId;
                }
                LOG.debug("Added open order. Order: " + m_orderMap.get(orderId).getOrder().action() + " " +
                        m_orderMap.get(orderId).getOrder().totalQuantity());
            }
        }
    }
    
    public void updateOrderStatus(int orderId, double filled, double remaining){
        synchronized(ORDERACCESSLOCK){
            if(m_orderMap.containsKey(orderId)){
                OrderInfo o = m_orderMap.get(orderId);
                o.setFilled(filled);
                o.setRemaining(remaining);
                m_orderMap.replace(orderId, o);
                LOG.debug("Updated order status. Order: " + m_orderMap.get(orderId).getOrder().action() + " " +
                    m_orderMap.get(orderId).getOrder().totalQuantity() + ", Filled: " + m_orderMap.get(orderId).getFilled());
                
                if(filled > 0.0){
                    // Partial fill occurred
                    synchronized(Trader.ORDERFILLMONITORLOCK){
                        if(!pendingCancelList.contains(orderId)){
                            pendingCancelList.add(orderId);
                            Trader.ORDERFILLMONITORLOCK.notifyAll();
                            LOG.debug("Added orderId " + orderId + " to pending cancel list. Notifying Trader");
                        }                        
                    }
                }
            }
            // Do not update status if no orderid is found
        }
    }
    
    public void processExecDetails(int orderId){
        synchronized(Trader.ORDERFILLMONITORLOCK){
            if(!pendingCancelList.contains(orderId)){
                pendingCancelList.add(orderId);
                Trader.ORDERFILLMONITORLOCK.notifyAll();
                LOG.debug("Added orderId " + orderId + " to pending cancel list. Notifying Trader");
            }
        }
    }
    
    public boolean verifyAndInitializeOrders(){
        // TODO: if need to synchonize for order verification
        LOG.debug("Verifying orders...");
        
        synchronized(OPENORDERLOCK){
            try {
                LOG.debug("Waiting for open order end to be received...");
                OPENORDERLOCK.wait();
            } catch (Exception e){
                LOG.error(e.getMessage(), e);
            }
        }
        
        LOG.debug("All orders are received, continue to verify order.");
        
        if(tradeConid == Integer.MAX_VALUE){
            findTradeConid();
        }
        
        if(m_orderMap.size() > 2){
            LOG.error("More than two orders are detected for source conid " + tradeConid + ", stopping program...");
            System.exit(0);
        }
        
        if(m_orderMap.isEmpty()){
            // No order found for TRADE_CONID, place new orders and return true
            LOG.info("No order is found for source conid, placing new BUY and SELL orders");
            placeNewBuyOrder();
            placeNewSellOrder();
            return true;
        } else {
            verifyOrders();
            
            // handle buy side order
            if(buyOrderId < Integer.MAX_VALUE){
                LOG.info("One BUY order is found. ");
                OrderInfo buyOrderInfo = m_orderMap.get(buyOrderId);
                if(buyOrderInfo.getFilled() > 0.0){
                    LOG.info("BUY order is partially filled, cancel and replace new order");
                    cancelCurrentOrderAndPlaceNewOrder(buyOrderId);
                } else {
                    updateCurrentBuyOrder();
                }
            } else {
                LOG.info("No BUY order is found. Placing new BUY order");
                placeNewBuyOrder();
            }
            
            if(sellOrderId < Integer.MAX_VALUE){
                LOG.info("One SELL order is found. ");
                OrderInfo sellOrderInfo = m_orderMap.get(sellOrderId);
                if(sellOrderInfo.getFilled() > 0.0){
                    LOG.info("Sell order is partially filled, cancel and replace new order");
                    cancelCurrentOrderAndPlaceNewOrder(sellOrderId);
                } else {
                    updateCurrentSellOrder();
                }
            } else {
                LOG.info("No SELL order is found. Placing new SELL order");
                placeNewSellOrder();
            }
            
            return true;
        }        
    }
    
    private void verifyOrders(){        
        Iterator it = m_orderMap.keySet().iterator();
        while(it.hasNext()){
            int orderId = (int) it.next();
            OrderInfo tmp = m_orderMap.get(orderId);
            if(tmp.getOrder().action() == Types.Action.BUY){
                if(buyOrderId < Integer.MAX_VALUE && buyOrderId != orderId){
                    LOG.error("Found more than two BUY orders for conid = " + tradeConid + ", stopping program...");
                    System.exit(0);
                }
            } else if(tmp.getOrder().action() == Types.Action.SELL){
                if(sellOrderId < Integer.MAX_VALUE && sellOrderId != orderId){
                    LOG.error("Found more than two SELL orders for conid = " + tradeConid + ", stopping program...");
                    System.exit(0);
                }
            }
        }
        
        LOG.debug("Orders are verified");
    }
    
    private void findTradeConid(){
        tradeConid = Integer.parseInt(m_configReader.getConfig(Configs.TRADE_CONID));
    }
    
    private void findTradeExchange(){
        tradeExchange = m_configReader.getConfig(Configs.TRADE_EXCHANGE);
    }
    
    private void findOrderSizeDefault(){
        orderSizeDefault = Integer.parseInt(m_configReader.getConfig(Configs.ORDER_SIZE_DEFAULT));
    }
    
    private void findAccount(){
        account = m_configReader.getConfig(Configs.ACCOUNT);
    }
    
    private void findPositionAdjustment(){
        positionAdjustment = Integer.parseInt(m_configReader.getConfig(Configs.POSITION_ADJUSTMENT));
    }
    
    private void placeNewBuyOrder(){
        // Buy order should be placed on the trade bid price
        try{
            double tradeBidPrice = m_quoteManager.getTradeBidPrice();
            while(tradeBidPrice < 0){
                LOG.debug("Cannot get trade bid price. Try again in 200 ms...");
                Thread.sleep(200);
            }
            
            if(orderSizeDefault == Integer.MAX_VALUE){
                findOrderSizeDefault();
            }
            
            if(positionAdjustment == Integer.MAX_VALUE){
                findPositionAdjustment();
            }
            
            double pos = m_positionManager.getPosition();
            int totalQuantity = 0;
            if(pos >= 0.0){
                totalQuantity = orderSizeDefault - positionAdjustment;
            } else {
                totalQuantity = orderSizeDefault;
            }
            
            if(account == null){
                findAccount();
            }
            
            // Double check order size
            if(totalQuantity < Integer.MAX_VALUE){
                int orderId = m_client.getCurrentOrderIdAndIncrement();
                Contract tradeContract = getTradeContract();
                Order order = getLimitOrder(Types.Action.BUY, totalQuantity, tradeBidPrice, account);
                m_client.getSocket().placeOrder(orderId, tradeContract, order);
                buyOrderId = orderId;
                LOG.info("Placed " + order.action() + " order (" + orderId + ") for " + tradeContract.conid() + "@" + tradeContract.exchange() + ": " + 
                        totalQuantity + "@" + tradeBidPrice);
            }
        } catch (Exception e){
            LOG.error(e.getMessage(), e);
        }
    }
    
    private void placeNewSellOrder(){
        // Sell order should be placed on the trade ask price
        try{
            double tradeAskPrice = m_quoteManager.getTradeAskPrice();
            while(tradeAskPrice < 0){
                LOG.debug("Cannot get trade ask price. Try again in 200 ms...");
                Thread.sleep(200);
            }
            
            if(orderSizeDefault == Integer.MAX_VALUE){
                findOrderSizeDefault();
            }
            
            if(positionAdjustment == Integer.MAX_VALUE){
                findPositionAdjustment();
            }
            
            double pos = m_positionManager.getPosition();
            int totalQuantity = 0;
            if(pos < 0.0){
                totalQuantity = orderSizeDefault - positionAdjustment;
            } else {
                totalQuantity = orderSizeDefault;
            }
            
            if(account == null){
                findAccount();
            }
            
            // Double check order size
            if(totalQuantity < Integer.MAX_VALUE){
                int orderId = m_client.getCurrentOrderIdAndIncrement();
                Contract tradeContract = getTradeContract();
                Order order = getLimitOrder(Types.Action.SELL, totalQuantity, tradeAskPrice, account);
                m_client.getSocket().placeOrder(orderId, tradeContract, order);
                sellOrderId = orderId;
                LOG.info("Placed " + order.action() + " order (" + orderId + ") for " + tradeContract.conid() + "@" + tradeContract.exchange() + ": " + 
                        totalQuantity + "@" + tradeAskPrice);
            }
        } catch (Exception e){
            LOG.error(e.getMessage(), e);
        }
    }
    
    private void updateCurrentBuyOrder(){
        // Buy order should be placed on the trade bid price
        try{
            double tradeBidPrice = m_quoteManager.getTradeBidPrice();
            while(tradeBidPrice < 0){
                LOG.debug("Cannot get trade bid price. Try again in 200 ms...");
                Thread.sleep(200);
            }
            
            // Only update price but not quantity, partial filled orders should be cancelled
            
            Order currentBuyOrder = m_orderMap.get(buyOrderId).getOrder();
            if(currentBuyOrder.lmtPrice() != tradeBidPrice){
                Contract tradeContract = getTradeContract();
                currentBuyOrder.lmtPrice(tradeBidPrice);
                m_client.getSocket().placeOrder(buyOrderId, tradeContract, currentBuyOrder);
                LOG.info("Modified " + currentBuyOrder.action() + " order (" + currentBuyOrder + ") for " + tradeContract.conid() + "@" + tradeContract.exchange() + ": " +
                    currentBuyOrder.totalQuantity() + "@" + tradeBidPrice);
            } else {
                LOG.debug("Current BUY order is up-to-date with the current market");
            }
        } catch (Exception e){
            LOG.error(e.getMessage(), e);
        }
    }
    
    private void updateCurrentSellOrder(){
        // Sell order should be placed on the trade ask price
        try{
            double tradeAskPrice = m_quoteManager.getTradeAskPrice();
            while(tradeAskPrice < 0){
                LOG.debug("Cannot get trade bid price. Try again in 200 ms...");
                Thread.sleep(200);
            }
            
            // Only update price but not quantity, partial filled orders should be cancelled
            
            Order currentSellOrder = m_orderMap.get(sellOrderId).getOrder();
            if(currentSellOrder.lmtPrice() != tradeAskPrice){
                Contract tradeContract = getTradeContract();
                currentSellOrder.lmtPrice(tradeAskPrice);
                m_client.getSocket().placeOrder(sellOrderId, tradeContract, currentSellOrder);
                LOG.info("Modified " + currentSellOrder.action() + " order (" + currentSellOrder + ") for " + tradeContract.conid() + "@" + tradeContract.exchange() + ": " +
                    currentSellOrder.totalQuantity() + "@" + tradeAskPrice);
            } else {
                LOG.debug("Current SELL order is up-to-date with the current market");
            }
        } catch (Exception e){
            LOG.error(e.getMessage(), e);
        }
    }
    
    public void cancelCurrentOrderAndPlaceNewOrder(int orderId){
        if(!m_orderMap.containsKey(orderId)){
            this.requestOpenOrder();
            try{
                Thread.sleep(200);
            } catch (Exception e){
                LOG.error(e.getMessage(), e);
            }
        }
        
        if(!m_orderMap.containsKey(orderId)){
            LOG.error("Cannot find orderId = " + orderId + " for cancellation");
            return;
        }
        LOG.debug("Order id = " + orderId + " has been added to pending cancel list");
        m_client.getSocket().cancelOrder(orderId);
        synchronized(CANCELORDERLOCK){
            try {
                LOG.debug("Waiting for cancel order confirmation...");
                CANCELORDERLOCK.wait();
                // TODO
                // What if cancel is failed
                // Handle system cancel
            } catch (Exception e){
                LOG.error(e.getMessage(), e);
            }
        }
        LOG.debug("Received order cancel confirmation. Removing order info from map");
        pendingCancelList.remove(orderId);
        if(m_orderMap.get(orderId).getOrder().action() == Types.Action.BUY){
            buyOrderId = Integer.MAX_VALUE;
            m_orderMap.remove(orderId);
            placeNewBuyOrder();
        } else if (m_orderMap.get(orderId).getOrder().action() == Types.Action.SELL){
            sellOrderId = Integer.MAX_VALUE;
            m_orderMap.remove(orderId);
            placeNewSellOrder();
        }        
    }
    
    private Contract getTradeContract(){
        if(tradeConid == Integer.MAX_VALUE){
            findTradeConid();
        }
        if(tradeExchange == null){
            findTradeExchange();
        }
        Contract tradeContract = new Contract();
        tradeContract.conid(tradeConid);
        tradeContract.exchange(tradeExchange);
        return tradeContract;
    }
    
    private Order getLimitOrder(Types.Action action, double totalQuantity, double lmtPrice, String account){
        Order order = new Order();
        order.action(action);
        order.orderType(OrderType.LMT);
        order.totalQuantity(totalQuantity);
        order.lmtPrice(lmtPrice);
        order.account(account);
        return order;
    }
    
    public List getPendingCancelList(){
        return pendingCancelList;
    }
    
    public void clearPendingCancelList(){
        pendingCancelList.clear();
    }
}

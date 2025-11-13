import React, { useEffect, useState, useRef } from 'react';
import { Line } from 'react-chartjs-2';
import {
    Chart as ChartJS,
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    Title,
    Tooltip,
    Legend,
    Filler
} from 'chart.js';

// Register Chart.js components (including scatter via PointElement + LineElement)
ChartJS.register(
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    Title,
    Tooltip,
    Legend,
    Filler
);

// Helper function to safely parse numbers
const safeParseFloat = (value, defaultValue = 0) => {
    if (value === null || value === undefined || value === '') return defaultValue;
    const parsed = parseFloat(value);
    return isNaN(parsed) ? defaultValue : parsed;
};

// Safely select an order time from multiple possible fields
const getOrderTime = (o) => {
    const candidates = [o?.updateTime, o?.time, o?.workingTime, o?.transactTime];
    const ts = candidates.find(t => typeof t === 'number' && t > 0);
    return ts || null;
};

// Status badge color styles
const statusBadgeStyles = (status) => {
    const base = { padding: '2px 8px', borderRadius: '10px', fontSize: '0.7rem', fontWeight: 600 };
    const s = (status || '').toUpperCase();
    switch (s) {
        case 'FILLED':
            return { ...base, border: '1px solid #16a34a', background: '#dcfce7', color: '#166534' };
        case 'PARTIALLY_FILLED':
            return { ...base, border: '1px solid #f59e0b', background: '#fef3c7', color: '#92400e' };
        case 'NEW':
            return { ...base, border: '1px solid #93c5fd', background: '#eff6ff', color: '#1d4ed8' };
        case 'CANCELED':
        case 'CANCELLED':
            return { ...base, border: '1px solid #ef4444', background: '#fee2e2', color: '#991b1b' };
        case 'REJECTED':
            return { ...base, border: '1px solid #dc2626', background: '#fee2e2', color: '#7f1d1d' };
        case 'EXPIRED':
            return { ...base, border: '1px solid #d1d5db', background: '#f3f4f6', color: '#374151' };
        default:
            return { ...base, border: '1px solid #d1d5db', background: '#f3f4f6', color: '#374151' };
    }
};

// Build a compact tooltip string from order meta + trade
const buildOrderTooltip = (meta, t) => {
    if (!meta && !t) return '';
    const parts = [];
    if (t?.orderId) parts.push(`Order #${t.orderId}`);
    if (meta?.type) parts.push(`Type: ${meta.type}${meta.timeInForce ? ' / ' + meta.timeInForce : ''}`);
    if (meta?.status) parts.push(`Status: ${meta.status}`);
    if (meta?.side) parts.push(`Side: ${meta.side}`);
    if (meta?.price != null) parts.push(`Price: $${safeParseFloat(meta.price).toFixed(2)}`);
    if (meta?.origQty != null) parts.push(`Orig: ${safeParseFloat(meta.origQty).toFixed(6)} ETH`);
    if (meta?.executedQty != null) parts.push(`Exec: ${safeParseFloat(meta.executedQty).toFixed(6)} ETH`);
    if (meta?.time) parts.push(`Time: ${new Date(meta.time).toLocaleString()}`);
    if (meta?.clientOrderId) parts.push(`ClientId: ${meta.clientOrderId}`);
    return parts.join(' | ');
};

function EthTrading() {
    const [ethPrice, setEthPrice] = useState(null);
    const [ticker24h, setTicker24h] = useState(null);
    const [priceHistory, setPriceHistory] = useState([]);
    const [portfolio, setPortfolio] = useState(null);
    const [trades, setTrades] = useState([]);
    const [orderTypeById, setOrderTypeById] = useState({}); // { [orderId]: 'TP' | 'SL' | 'OTHER' }
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    
    // Trading state
    const [buyAmount, setBuyAmount] = useState('');
    const [sellAmount, setSellAmount] = useState('');
    const [tradeReason, setTradeReason] = useState('');
    const [tradeMessage, setTradeMessage] = useState(null);
    const [latestRec, setLatestRec] = useState(null);
    const [openOrders, setOpenOrders] = useState([]);
    const [fees, setFees] = useState(null);
    const [recs, setRecs] = useState([]);
    const [entryOcoByOrderId, setEntryOcoByOrderId] = useState({}); // { [entryOrderId]: { ocoOrderListId, exitOrders } }
    const [recMetaByOrderId, setRecMetaByOrderId] = useState({}); // { [entryOrderId]: { entryPlacedAt, timeHorizonMinutes } }
    const [orderMetaById, setOrderMetaById] = useState({}); // { [orderId]: { type, status, timeInForce } }
    
    // Trade expansion state
    const [expandedTrades, setExpandedTrades] = useState({});
    
    // AI Chat state
    const [chatMessages, setChatMessages] = useState([]);
    const [chatInput, setChatInput] = useState('');
    const [chatLoading, setChatLoading] = useState(false);
    const chatEndRef = useRef(null);

    // Fetch current price
    const fetchETHPrice = async () => {
        try {
            const response = await fetch("/api/trading/eth/price", {
                method: "GET",
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token")
                }
            });

            if (response.status === 200) {
                const data = await response.json();
                setEthPrice(data);
                setError(null);
            } else {
                setError(`Failed to fetch price: ${response.status}`);
            }
        } catch (err) {
            setError(`Error: ${err.message}`);
        }
    };

    // Fetch recent recommendations to map entry orders -> OCO metadata
    const fetchRecommendations = async () => {
        try {
            const response = await fetch("/api/trading/recommendations?limit=50", {
                method: "GET",
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token")
                }
            });
            if (response.ok) {
                const data = await response.json();
                const list = Array.isArray(data) ? data : [];
                setRecs(list);
                // Build map: entryOrderId -> { ocoOrderListId, exitOrders }
                const ocoMap = {};
                const metaMap = {};
                list.forEach(r => {
                    if (!r) return;
                    if (r.entryOrderId && r.ocoOrderListId) {
                        ocoMap[r.entryOrderId] = { ocoOrderListId: r.ocoOrderListId, exitOrders: r.exitOrders || null };
                    }
                    if (r.entryOrderId) {
                        metaMap[r.entryOrderId] = {
                            entryPlacedAt: r.entryPlacedAt || null,
                            timeHorizonMinutes: r.timeHorizonMinutes || null
                        };
                    }
                });
                setEntryOcoByOrderId(ocoMap);
                setRecMetaByOrderId(metaMap);
            }
        } catch (err) {
            console.error("Error fetching recommendations:", err);
        }
    };

    // Classify realized OCO sells (TP vs SL) by fetching their order type
    useEffect(() => {
        const classify = async () => {
            try {
                const ocoSells = trades
                    .filter(t => (t.orderListId !== null && t.orderListId !== undefined) && !t.isBuyer)
                    .slice(-50); // limit
                const toFetch = ocoSells.filter(t => !orderTypeById[t.orderId]);
                if (toFetch.length === 0) return;

                const updates = {};
                await Promise.all(
                    toFetch.map(async (t) => {
                        try {
                            const resp = await fetch(`/api/trading/order?symbol=ETHUSDC&orderId=${t.orderId}`, {
                                method: 'GET',
                                headers: { 'Authorization': 'Bearer ' + sessionStorage.getItem('token') }
                            });
                            if (resp.ok) {
                                const ord = await resp.json();
                                const type = (ord.type || '').toUpperCase();
                                let leg = 'OTHER';
                                if (type === 'LIMIT') leg = 'TP';
                                else if (type.includes('STOP')) leg = 'SL';
                                updates[t.orderId] = leg;
                            }
                        } catch (e) {
                            // ignore network errors, try next time
                        }
                    })
                );
                if (Object.keys(updates).length > 0) {
                    setOrderTypeById(prev => ({ ...prev, ...updates }));
                }
            } catch (e) {
                // silent fail
            }
        };
        if (trades && trades.length) classify();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [trades]);

    // Fetch order meta (type/status/TIF) for recent trades to display in history
    useEffect(() => {
        const fetchMeta = async () => {
            try {
                const recent = [...trades].slice(-50);
                const missing = recent.filter(t => t.orderId && !orderMetaById[t.orderId]);
                if (missing.length === 0) return;

                const metaUpdates = {};
                const legUpdates = {};
                await Promise.all(
                    missing.map(async (t) => {
                        try {
                            const resp = await fetch(`/api/trading/order?symbol=ETHUSDC&orderId=${t.orderId}`, {
                                method: 'GET',
                                headers: { 'Authorization': 'Bearer ' + sessionStorage.getItem('token') }
                            });
                            if (resp.ok) {
                                const ord = await resp.json();
                                metaUpdates[t.orderId] = {
                                    type: ord.type || null,
                                    status: ord.status || null,
                                    timeInForce: ord.timeInForce || null,
                                    price: ord.price ?? null,
                                    origQty: ord.origQty ?? null,
                                    executedQty: ord.executedQty ?? null,
                                    side: ord.side ?? null,
                                    clientOrderId: ord.clientOrderId ?? null,
                                    time: ord.time || ord.transactTime || ord.updateTime || null,
                                };
                                // Also compute OCO leg classification if relevant
                                if (!t.isBuyer && (t.orderListId !== null && t.orderListId !== undefined)) {
                                    const upType = (ord.type || '').toUpperCase();
                                    let leg = 'OTHER';
                                    if (upType === 'LIMIT') leg = 'TP';
                                    else if (upType.includes('STOP')) leg = 'SL';
                                    legUpdates[t.orderId] = leg;
                                }
                            }
                        } catch (e) {
                            // ignore
                        }
                    })
                );
                if (Object.keys(metaUpdates).length > 0) setOrderMetaById(prev => ({ ...prev, ...metaUpdates }));
                if (Object.keys(legUpdates).length > 0) setOrderTypeById(prev => ({ ...prev, ...legUpdates }));
            } catch (e) { /* ignore */ }
        };
        if (trades && trades.length) fetchMeta();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [trades]);

    // Fetch fees (maker/taker) for ETHUSDC
    const fetchFees = async () => {
        try {
            const response = await fetch("/api/trading/fees?symbol=ETHUSDC", {
                method: "GET",
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token")
                }
            });
            if (response.ok) {
                const data = await response.json();
                setFees(data);
            }
        } catch (err) {
            console.error("Error fetching fees:", err);
        }
    };

    // Cancel a specific order then refresh open orders
    const handleCancelOrder = async (order) => {
        try {
            const response = await fetch('/api/trading/cancel-order', {
                method: 'POST',
                headers: {
                    'Authorization': 'Bearer ' + sessionStorage.getItem('token'),
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    symbol: order.symbol || 'ETHUSDC',
                    orderId: order.orderId,
                    clientOrderId: order.clientOrderId || null
                })
            });
            if (response.ok) {
                await fetchOpenOrders();
            } else {
                console.error('Cancel order failed', response.status);
            }
        } catch (e) {
            console.error('Cancel order error:', e);
        }
    };

    // Fetch open orders (ETHUSDC by default)
    const fetchOpenOrders = async () => {
        try {
            const response = await fetch("/api/trading/open-orders?symbol=ETHUSDC", {
                method: "GET",
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token")
                }
            });
            if (response.ok) {
                const data = await response.json();
                setOpenOrders(Array.isArray(data) ? data : []);
            }
        } catch (err) {
            console.error("Error fetching open orders:", err);
        }
    };

    // Fetch latest AI recommendation (limit=1)
    const fetchLatestRecommendation = async () => {
        try {
            const response = await fetch("/api/trading/recommendations?limit=1", {
                method: "GET",
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token")
                }
            });
            if (response.ok) {
                const data = await response.json();
                setLatestRec((data && data.length > 0) ? data[0] : null);
            }
        } catch (err) {
            console.error("Error fetching latest recommendation:", err);
        }
    };

    // Refresh OCO status for the latest recommendation
    const handleRefreshOcoStatus = async () => {
        if (!latestRec || !latestRec.id) return;
        try {
            const response = await fetch(`/api/trading/oco-status?recId=${latestRec.id}`, {
                method: 'GET',
                headers: {
                    'Authorization': 'Bearer ' + sessionStorage.getItem('token')
                }
            });
            if (response.ok) {
                const data = await response.json();
                setLatestRec(prev => prev ? ({ ...prev, ocoOrderListId: data.orderListId, exitOrders: data.exitOrders }) : prev);
            }
        } catch (e) {
            console.error('Refresh OCO status error:', e);
        }
    };

    // Fetch 24h ticker stats
    const fetchETHTicker = async () => {
        try {
            const response = await fetch("/api/trading/eth/ticker24h", {
                method: "GET",
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token")
                }
            });

            if (response.status === 200) {
                const data = await response.json();
                setTicker24h(data);
            }
        } catch (err) {
            console.error("Error fetching ticker:", err);
        }
    };

    // Fetch kline/candlestick data for chart
    const fetchETHKlines = async () => {
        try {
            const response = await fetch("/api/trading/eth/klines?interval=1h&limit=24", {
                method: "GET",
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token")
                }
            });

            if (response.status === 200) {
                const data = await response.json();
                // Kline format: [openTime, open, high, low, close, volume, closeTime, ...]
                const prices = data.map(kline => ({
                    time: new Date(kline[0]),
                    price: parseFloat(kline[4]) // close price
                }));
                setPriceHistory(prices);
            }
        } catch (err) {
            console.error("Error fetching klines:", err);
        } finally {
            setLoading(false);
        }
    };

    // Fetch portfolio
    const fetchPortfolio = async () => {
        try {
            const response = await fetch("/api/trading/portfolio", {
                method: "GET",
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token")
                }
            });
            if (response.status === 200) {
                const data = await response.json();
                console.log('Portfolio data:', data);
                console.log('USDC Balance (usdcBalance) type:', typeof data.usdcBalance, 'Value:', data.usdcBalance);
                console.log('USD Balance (usdBalance) type:', typeof data.usdBalance, 'Value:', data.usdBalance);
                console.log('ETH Balance type:', typeof data.ethBalance, 'Value:', data.ethBalance);
                console.log('Total Value:', data.totalValue);
                setPortfolio(data);
            }
        } catch (err) {
            console.error("Error fetching portfolio:", err);
        }
    };

    // Fetch trade history
    const fetchTrades = async () => {
        try {
            const response = await fetch("/api/trading/trades", {
                method: "GET",
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token")
                }
            });
            if (response.status === 200) {
                const data = await response.json();
                console.log('Trades data:', data);
                if (data.length > 0) {
                    console.log('First trade:', data[0]);
                    console.log('Trade fields:', Object.keys(data[0]));
                    console.log('Qty type:', typeof data[0].qty, 'Value:', data[0].qty);
                    console.log('Price type:', typeof data[0].price, 'Value:', data[0].price);
                    console.log('Time type:', typeof data[0].time, 'Value:', data[0].time);
                    console.log('Time as Date:', new Date(data[0].time).toLocaleString());
                    console.log('isBuyer:', data[0].isBuyer, '→ Type:', data[0].isBuyer ? 'BUY' : 'SELL');
                }
                setTrades(data);
            }
        } catch (err) {
            console.error("Error fetching trades:", err);
        }
    };

    // Execute BUY trade
    const handleBuy = async () => {
        if (!buyAmount || parseFloat(buyAmount) <= 0) {
            setTradeMessage({ type: 'error', text: 'Please enter a valid USD amount' });
            return;
        }

        try {
            const response = await fetch("/api/trading/buy", {
                method: "POST",
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token"),
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    amount: buyAmount,
                    reason: tradeReason || 'Manual buy'
                })
            });

            if (response.status === 200) {
                const trade = await response.json();
                setTradeMessage({ 
                    type: 'success', 
                    text: `✅ Bought ${parseFloat(trade.quantity).toFixed(6)} ETH at $${parseFloat(trade.price).toFixed(2)}` 
                });
                setBuyAmount('');
                setTradeReason('');
                fetchPortfolio();
                fetchTrades();
            } else {
                const error = await response.json();
                setTradeMessage({ type: 'error', text: error.error || 'Trade failed' });
            }
        } catch (err) {
            setTradeMessage({ type: 'error', text: 'Trade execution error' });
        }
    };

    // Execute SELL trade
    const handleSell = async () => {
        if (!sellAmount || parseFloat(sellAmount) <= 0) {
            setTradeMessage({ type: 'error', text: 'Please enter a valid ETH amount' });
            return;
        }

        try {
            const response = await fetch("/api/trading/sell", {
                method: "POST",
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token"),
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    amount: sellAmount,
                    reason: tradeReason || 'Manual sell'
                })
            });

            if (response.status === 200) {
                const trade = await response.json();
                setTradeMessage({ 
                    type: 'success', 
                    text: `✅ Sold ${parseFloat(trade.quantity).toFixed(6)} ETH at $${parseFloat(trade.price).toFixed(2)}` 
                });
                setSellAmount('');
                setTradeReason('');
                fetchPortfolio();
                fetchTrades();
            } else {
                const error = await response.json();
                setTradeMessage({ type: 'error', text: error.error || 'Trade failed' });
            }
        } catch (err) {
            setTradeMessage({ type: 'error', text: 'Trade execution error' });
        }
    };

    // Send AI chat message
    const handleSendChat = async (messageOverride = null) => {
        const messageToSend = messageOverride || chatInput;
        if (!messageToSend.trim()) return;

        // Only add user message if it's from the input field (not a button trigger)
        if (!messageOverride) {
            const userMsg = { role: 'user', content: chatInput };
            setChatMessages(prev => [...prev, userMsg]);
        } else {
            // For button triggers, add a system-style message
            setChatMessages(prev => [...prev, { role: 'user', content: '🔍 Requesting market analysis...' }]);
        }
        
        setChatInput('');
        setChatLoading(true);

        try {
            const response = await fetch("/api/chat/trading", {
                method: "POST",
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token"),
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({ message: messageToSend })
            });

            if (response.status === 200) {
                const data = await response.json();
                setChatMessages(prev => [...prev, { role: 'assistant', content: data.response }]);
                // Refresh portfolio after AI response (in case it executed a trade)
                fetchPortfolio();
                fetchTrades();
            } else {
                setChatMessages(prev => [...prev, { role: 'assistant', content: 'Error: Failed to get response' }]);
            }
        } catch (err) {
            setChatMessages(prev => [...prev, { role: 'assistant', content: 'Error: ' + err.message }]);
        } finally {
            setChatLoading(false);
        }
    };

    // Trigger market analysis
    const handleAnalyzeMarket = () => {
        const analysisPrompt = `Perform a comprehensive market analysis for ETH right now:

1. Check current market data (price, 24h change, volume)
2. Review my portfolio and current positions
3. Analyze if this is a good time to BUY, SELL, or HOLD
4. Provide specific reasoning based on the data
5. If recommending a trade, suggest position size and price targets

Be specific and actionable. Include confidence level (HIGH/MEDIUM/LOW).`;

        handleSendChat(analysisPrompt);
    };

    useEffect(() => {
        // Scroll to bottom of chat
        chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [chatMessages]);

    useEffect(() => {
        // Initial fetch
        fetchETHPrice();
        fetchETHTicker();
        fetchETHKlines();
        fetchPortfolio();
        fetchTrades();
        fetchLatestRecommendation();
        fetchRecommendations();
        fetchOpenOrders();
        fetchFees();

        // Refresh price every 10 seconds
        const priceInterval = setInterval(fetchETHPrice, 10000);
        // Refresh chart every 60 seconds
        const chartInterval = setInterval(fetchETHKlines, 60000);
        // Refresh portfolio every 30 seconds
        const portfolioInterval = setInterval(fetchPortfolio, 30000);
        const recInterval = setInterval(fetchLatestRecommendation, 30000);
        const recsListInterval = setInterval(fetchRecommendations, 60000);
        const openOrdersInterval = setInterval(fetchOpenOrders, 30000);
        const feesInterval = setInterval(fetchFees, 600000);

        return () => {
            clearInterval(priceInterval);
            clearInterval(chartInterval);
            clearInterval(portfolioInterval);
            clearInterval(recInterval);
            clearInterval(openOrdersInterval);
            clearInterval(feesInterval);
            clearInterval(recsListInterval);
        };
    }, []);

    // Ticking clock for countdowns
    const [nowMs, setNowMs] = useState(Date.now());
    useEffect(() => {
        const id = setInterval(() => setNowMs(Date.now()), 1000);
        return () => clearInterval(id);
    }, []);

    const formatCountdown = (ms) => {
        if (ms <= 0) return 'Expired';
        const totalSec = Math.floor(ms / 1000);
        const hours = Math.floor(totalSec / 3600);
        const minutes = Math.floor((totalSec % 3600) / 60);
        const seconds = totalSec % 60;
        if (hours > 0) {
            return `${hours}h ${minutes.toString().padStart(2, '0')}m ${seconds.toString().padStart(2, '0')}s`;
        }
        return `${minutes.toString().padStart(1, '0')}m ${seconds.toString().padStart(2, '0')}s`;
    };

    const getExpiryInfoForOrder = (order) => {
        if (!order || !order.orderId) return null;
        const meta = recMetaByOrderId[order.orderId];
        if (!meta || !meta.timeHorizonMinutes) return null;
        let placedMs = null;
        if (meta.entryPlacedAt) {
            const d = new Date(meta.entryPlacedAt);
            if (!isNaN(d.getTime())) placedMs = d.getTime();
        }
        if (!placedMs) {
            const t = getOrderTime(order);
            if (t) placedMs = t;
        }
        if (!placedMs) return null;
        const expiryMs = placedMs + meta.timeHorizonMinutes * 60 * 1000;
        const remainingMs = expiryMs - nowMs;
        return { placedMs, expiryMs, remainingMs, horizonMin: meta.timeHorizonMinutes };
    };

    if (loading) {
        return <div style={{ padding: '2rem', textAlign: 'center' }}>Loading ETH data...</div>;
    }

    if (error) {
        return <div style={{ padding: '2rem', color: 'red' }}>Error: {error}</div>;
    }

    // Calculate average ETH cost basis from trades
    const calculateAverageCost = () => {
        if (!trades || trades.length === 0 || !portfolio || portfolio.ethBalance === 0) return null;
        
        // Get all buy trades, sorted by time
        const buyTrades = trades.filter(t => t.isBuyer).sort((a, b) => a.time - b.time);
        if (buyTrades.length === 0) return null;
        
        // Calculate weighted average cost based on current holdings
        let totalCost = 0;
        let totalQty = 0;
        
        // Process trades in order to track current cost basis
        buyTrades.forEach(trade => {
            const qty = safeParseFloat(trade.quantity || trade.qty);
            const price = safeParseFloat(trade.price);
            totalCost += qty * price;
            totalQty += qty;
        });
        
        return totalQty > 0 ? totalCost / totalQty : null;
    };
    
    const averageCost = calculateAverageCost();
    
    // Filter trades that fall within the 24h chart window
    const chartStartTime = priceHistory.length > 0 ? priceHistory[0].time.getTime() : Date.now() - 24*60*60*1000;
    const chartEndTime = priceHistory.length > 0 ? priceHistory[priceHistory.length - 1].time.getTime() : Date.now();
    
    const recentTrades = trades.filter(trade => {
        const tradeTime = trade.time;
        return tradeTime >= chartStartTime && tradeTime <= chartEndTime;
    });
    
    // Create chart labels
    const chartLabels = priceHistory.map(p => p.time.toLocaleTimeString());
    
    // Create sparse arrays for buy/sell markers aligned with chart labels
    // Initialize with null for all positions
    const buyMarkersData = new Array(chartLabels.length).fill(null);
    const sellMarkersData = new Array(chartLabels.length).fill(null);
    
    // Place markers at correct index by finding matching or closest time
    recentTrades.forEach(trade => {
        const tradeLabel = new Date(trade.time).toLocaleTimeString();
        let index = chartLabels.findIndex(label => label === tradeLabel);
        
        // If no exact match, find closest time
        if (index === -1 && priceHistory.length > 0) {
            const tradeTime = trade.time;
            let closestIndex = 0;
            let minDiff = Math.abs(priceHistory[0].time.getTime() - tradeTime);
            
            for (let i = 1; i < priceHistory.length; i++) {
                const diff = Math.abs(priceHistory[i].time.getTime() - tradeTime);
                if (diff < minDiff) {
                    minDiff = diff;
                    closestIndex = i;
                }
            }
            
            // Only use closest if within 5 minutes (300000ms)
            if (minDiff < 300000) {
                index = closestIndex;
            }
        }
        
        if (index !== -1) {
            const price = safeParseFloat(trade.price);
            if (trade.isBuyer) {
                buyMarkersData[index] = price;
            } else {
                sellMarkersData[index] = price;
            }
        }
    });
    
    const hasBuyMarkers = buyMarkersData.some(v => v !== null);
    const hasSellMarkers = sellMarkersData.some(v => v !== null);
    
    // OCO overlays for line chart
    const tpPrice = latestRec && latestRec.takeProfit1 ? safeParseFloat(latestRec.takeProfit1) : null;
    const slPrice = latestRec && latestRec.stopLoss ? safeParseFloat(latestRec.stopLoss) : null;

    // Realized OCO sells aligned to labels with leg classification
    const ocoSellTpMarkersData = new Array(chartLabels.length).fill(null);
    const ocoSellSlMarkersData = new Array(chartLabels.length).fill(null);
    trades
        .filter(t => (t.orderListId !== null && t.orderListId !== undefined) && !t.isBuyer)
        .forEach(trade => {
            const tradeLabel = new Date(trade.time).toLocaleTimeString();
            let index = chartLabels.findIndex(label => label === tradeLabel);
            if (index === -1 && priceHistory.length > 0) {
                const tradeTime = trade.time;
                let closestIndex = 0;
                let minDiff = Math.abs(priceHistory[0].time.getTime() - tradeTime);
                for (let i = 1; i < priceHistory.length; i++) {
                    const diff = Math.abs(priceHistory[i].time.getTime() - tradeTime);
                    if (diff < minDiff) {
                        minDiff = diff;
                        closestIndex = i;
                    }
                }
                if (minDiff < 300000) index = closestIndex; // within 5 minutes
            }
            if (index !== -1) {
                const leg = orderTypeById[trade.orderId];
                if (leg === 'TP') {
                    ocoSellTpMarkersData[index] = safeParseFloat(trade.price);
                } else if (leg === 'SL') {
                    ocoSellSlMarkersData[index] = safeParseFloat(trade.price);
                }
            }
        });

    // Open orders overlay (current OCO targets)
    const openTpPrices = Array.from(new Set(
        openOrders
            .filter(o => (o.side === 'SELL') && (o.type === 'LIMIT'))
            .map(o => Number(safeParseFloat(o.price).toFixed(2)))
            .filter(v => v > 0)
    ));
    const openSlPrices = Array.from(new Set(
        openOrders
            .filter(o => (o.side === 'SELL') && (o.type && o.type.includes('STOP')))
            .map(o => Number(safeParseFloat(o.stopPrice || o.price).toFixed(2)))
            .filter(v => v > 0)
    ));

    // Build line chart with OCO overlays
    const chartData = {
        labels: chartLabels,
        datasets: [
            {
                label: 'ETH/USDC Price',
                data: priceHistory.map(p => p.price),
                borderColor: '#667eea',
                backgroundColor: 'rgba(102, 126, 234, 0.1)',
                borderWidth: 2,
                tension: 0.4,
                fill: true,
                order: 4
            },
            ...(averageCost ? [{
                label: `Avg Cost: $${averageCost.toFixed(2)}`,
                data: priceHistory.map(() => averageCost),
                borderColor: '#f59e0b',
                backgroundColor: 'transparent',
                borderWidth: 2,
                borderDash: [5, 5],
                pointRadius: 0,
                fill: false,
                order: 1
            }] : []),
            ...(tpPrice ? [{
                label: `OCO TP Target ($${tpPrice.toFixed(2)})`,
                data: priceHistory.map(() => tpPrice),
                borderColor: '#10b981',
                backgroundColor: 'transparent',
                borderDash: [6, 6],
                borderWidth: 2,
                pointRadius: 0,
                fill: false,
                order: 2
            }] : []),
            ...(slPrice ? [{
                label: `OCO SL Target ($${slPrice.toFixed(2)})`,
                data: priceHistory.map(() => slPrice),
                borderColor: '#ef4444',
                backgroundColor: 'transparent',
                borderDash: [6, 6],
                borderWidth: 2,
                pointRadius: 0,
                fill: false,
                order: 2
            }] : []),
            // Open Orders overlay (TP lines)
            ...openTpPrices.map((lvl, idx) => ({
                label: `Open TP #${idx + 1} ($${lvl.toFixed(2)})`,
                data: priceHistory.map(() => lvl),
                borderColor: '#16a34a',
                backgroundColor: 'transparent',
                borderDash: [3, 4],
                borderWidth: 1.5,
                pointRadius: 0,
                fill: false,
                order: 2
            })),
            // Open Orders overlay (SL lines)
            ...openSlPrices.map((lvl, idx) => ({
                label: `Open SL #${idx + 1} ($${lvl.toFixed(2)})`,
                data: priceHistory.map(() => lvl),
                borderColor: '#dc2626',
                backgroundColor: 'transparent',
                borderDash: [3, 4],
                borderWidth: 1.5,
                pointRadius: 0,
                fill: false,
                order: 2
            })),
            ...(hasBuyMarkers ? [{
                label: 'Buy',
                data: buyMarkersData,
                showLine: false,
                backgroundColor: '#10b981',
                borderColor: '#059669',
                borderWidth: 2,
                pointRadius: 8,
                pointHoverRadius: 10,
                pointStyle: 'circle',
                spanGaps: false,
                order: 0
            }] : []),
            ...(hasSellMarkers ? [{
                label: 'Sell',
                data: sellMarkersData,
                showLine: false,
                backgroundColor: '#ef4444',
                borderColor: '#dc2626',
                borderWidth: 2,
                pointRadius: 8,
                pointHoverRadius: 10,
                pointStyle: 'circle',
                spanGaps: false,
                order: 0
            }] : []),
            ...(ocoSellTpMarkersData.some(v => v !== null) ? [{
                label: 'OCO TP Fills',
                data: ocoSellTpMarkersData,
                showLine: false,
                backgroundColor: '#22c55e',
                borderColor: '#16a34a',
                borderWidth: 2,
                pointRadius: 9,
                pointHoverRadius: 11,
                pointStyle: 'triangle',
                spanGaps: false,
                order: 0
            }] : []),
            ...(ocoSellSlMarkersData.some(v => v !== null) ? [{
                label: 'OCO SL Fills',
                data: ocoSellSlMarkersData,
                showLine: false,
                backgroundColor: '#f97316',
                borderColor: '#ea580c',
                borderWidth: 2,
                pointRadius: 9,
                pointHoverRadius: 11,
                pointStyle: 'triangle',
                spanGaps: false,
                order: 0
            }] : [])
        ]
    };

    const chartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: {
                display: true,
                position: 'top'
            },
            tooltip: {
                mode: 'index',
                intersect: false
            }
        },
        scales: {
            y: {
                ticks: {
                    callback: function(value) {
                        return '$' + value.toLocaleString();
                    }
                }
            }
        }
    };

    const priceChange = ticker24h ? parseFloat(ticker24h.priceChange) : 0;
    const priceChangePercent = ticker24h ? parseFloat(ticker24h.priceChangePercent) : 0;
    const isPositive = priceChange >= 0;

    // Compute OCO status summary for display from exitOrders
    const summarizeOcoStatus = (exitOrders) => {
        if (!exitOrders) return 'CREATED';
        const statuses = Object.keys(exitOrders)
            .filter(k => k.toLowerCase().includes('status'))
            .map(k => (exitOrders[k] || '').toUpperCase());
        if (statuses.length === 0) return 'CREATED';
        if (statuses.some(s => s === 'FILLED')) return 'FILLED';
        const pendingSet = new Set(['NEW', 'PARTIALLY_FILLED', 'PENDING_NEW', 'ACCEPTED']);
        if (statuses.some(s => pendingSet.has(s))) return 'PENDING';
        const closedSet = new Set(['CANCELED', 'EXPIRED', 'REJECTED']);
        if (statuses.length > 0 && statuses.every(s => closedSet.has(s))) return 'CANCELED';
        return 'CREATED';
    };

    const ocoBadgeStyles = (status) => {
        const base = { padding: '2px 8px', borderRadius: '10px', fontSize: '0.7rem', fontWeight: 600, border: '1px solid', display: 'inline-block' };
        switch (status) {
            case 'PENDING':
                return { ...base, background: '#e0f2fe', color: '#0369a1', borderColor: '#38bdf8' };
            case 'FILLED':
                return { ...base, background: '#dcfce7', color: '#166534', borderColor: '#22c55e' };
            case 'CANCELED':
                return { ...base, background: '#f3f4f6', color: '#374151', borderColor: '#9ca3af' };
            default:
                return { ...base, background: '#fef3c7', color: '#92400e', borderColor: '#f59e0b' };
        }
    };

    return (
        <div style={{ padding: '2rem', maxWidth: '1400px', margin: '0 auto' }}>
            <h1 style={{ textAlign: 'center', marginBottom: '2rem' }}>
                📈 ETH Trading Dashboard
            </h1>

            {/* Price Cards */}
            <div style={{ 
                display: 'grid', 
                gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', 
                gap: '1.5rem',
                marginBottom: '2rem'
            }}>
                {/* Current Price Card */}
                <div style={{
                    background: 'white',
                    borderRadius: '12px',
                    padding: '1.5rem',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
                }}>
                    <div style={{ fontSize: '0.9rem', color: '#666', marginBottom: '0.5rem' }}>
                        Current Price
                    </div>
                    <div style={{ fontSize: '2rem', fontWeight: 'bold', color: '#333' }}>
                        ${ethPrice ? parseFloat(ethPrice.price).toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2}) : '---'}
                    </div>
                    <div style={{ fontSize: '0.85rem', color: '#999', marginTop: '0.5rem' }}>
                        ETH/USDC
                    </div>
                </div>

                {/* Portfolio Card */}
                {portfolio && (
                    <div style={{
                        background: 'white',
                        borderRadius: '12px',
                        padding: '1.5rem',
                        boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
                    }}>
                        <div style={{ fontSize: '0.9rem', color: '#666', marginBottom: '0.5rem' }}>
                            Portfolio
                        </div>
                        {(() => {
                            const usdcFree = safeParseFloat(portfolio.usdcBalance);
                            const usdcLocked = safeParseFloat(portfolio.usdcLocked);
                            const usdcTotal = safeParseFloat(portfolio.usdcTotal || (usdcFree + usdcLocked));
                            const ethFree = safeParseFloat(portfolio.ethBalance);
                            const ethLocked = safeParseFloat(portfolio.ethLocked);
                            const ethTotal = safeParseFloat(portfolio.ethTotal || (ethFree + ethLocked));
                            const totalFree = safeParseFloat(portfolio.totalValueFree || portfolio.totalValue);
                            const totalTotal = safeParseFloat(portfolio.totalValueTotal || (usdcTotal + ethTotal * safeParseFloat(portfolio.ethPrice)));
                            return (
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                                    <div>
                                        <div style={{ color: '#888' }}>USDC</div>
                                        <div style={{ fontWeight: 600 }}>
                                            Free: ${usdcFree.toFixed(2)}
                                        </div>
                                        <div style={{ color: '#666', fontSize: '0.9rem' }}>
                                            Locked: ${usdcLocked.toFixed(2)}
                                        </div>
                                        <div style={{ color: '#333' }}>
                                            Total: ${usdcTotal.toFixed(2)}
                                        </div>
                                    </div>
                                    <div>
                                        <div style={{ color: '#888' }}>ETH</div>
                                        <div style={{ fontWeight: 600 }}>
                                            Free: {ethFree.toFixed(6)} ETH
                                        </div>
                                        <div style={{ color: '#666', fontSize: '0.9rem' }}>
                                            Locked: {ethLocked.toFixed(6)} ETH
                                        </div>
                                        <div style={{ color: '#333' }}>
                                            Total: {ethTotal.toFixed(6)} ETH
                                        </div>
                                    </div>
                                    <div style={{ gridColumn: '1 / span 2', borderTop: '1px solid #eee', paddingTop: '0.5rem' }}>
                                        <div style={{ color: '#888' }}>Total Value</div>
                                        <div style={{ fontWeight: 600 }}>
                                            Free: ${totalFree.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                        </div>
                                        <div style={{ color: '#333' }}>
                                            Total: ${totalTotal.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                        </div>
                                    </div>
                                </div>
                            );
                        })()}
                    </div>
                )}

                {/* Fees Card */}
                {fees && (
                    <div style={{
                        background: 'white',
                        borderRadius: '12px',
                        padding: '1.5rem',
                        boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
                    }}>
                        <div style={{ fontSize: '0.9rem', color: '#666', marginBottom: '0.5rem' }}>
                            Fees
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem' }}>
                            <div>
                                <div style={{ color: '#888' }}>Maker</div>
                                <div style={{ fontWeight: '600' }}>{(safeParseFloat(fees.maker) * 100).toFixed(2)}%</div>
                            </div>
                            <div>
                                <div style={{ color: '#888' }}>Taker</div>
                                <div style={{ fontWeight: '600' }}>{(safeParseFloat(fees.taker) * 100).toFixed(2)}%</div>
                            </div>
                            <div style={{ gridColumn: '1 / span 2', borderTop: '1px solid #eee', paddingTop: '0.5rem' }}>
                                <div style={{ color: '#888' }}>Round-trip (taker+taker)</div>
                                <div style={{ fontWeight: '600' }}>{(safeParseFloat(fees.taker) * 2 * 100).toFixed(2)}%</div>
                            </div>
                        </div>
                        <div style={{ marginTop: '0.5rem', fontSize: '0.8rem', color: '#999' }}>Assumes taker+taker for conservative break-even.</div>
                    </div>
                )}

                {/* 24h Change Card */}
                {ticker24h && (
                    <div style={{
                        background: 'white',
                        borderRadius: '12px',
                        padding: '1.5rem',
                        boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
                    }}>
                        <div style={{ fontSize: '0.9rem', color: '#666', marginBottom: '0.5rem' }}>
                            24h Change
                        </div>
                        <div style={{ 
                            fontSize: '2rem', 
                            fontWeight: 'bold', 
                            color: isPositive ? '#10b981' : '#ef4444' 
                        }}>
                            {isPositive ? '+' : ''}{priceChangePercent.toFixed(2)}%
                        </div>
                        <div style={{ 
                            fontSize: '0.9rem', 
                            color: isPositive ? '#10b981' : '#ef4444',
                            marginTop: '0.5rem'
                        }}>
                            {isPositive ? '+' : ''}${priceChange.toFixed(2)}
                        </div>
                    </div>
                )}

                {/* 24h High/Low Card */}
                {ticker24h && (
                    <div style={{
                        background: 'white',
                        borderRadius: '12px',
                        padding: '1.5rem',
                        boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
                    }}>
                        <div style={{ fontSize: '0.9rem', color: '#666', marginBottom: '0.5rem' }}>
                            24h Range
                        </div>
                        <div style={{ fontSize: '1.1rem', fontWeight: '600', color: '#333' }}>
                            <span style={{ color: '#10b981' }}>H: ${parseFloat(ticker24h.highPrice).toLocaleString()}</span>
                        </div>
                        <div style={{ fontSize: '1.1rem', fontWeight: '600', color: '#333', marginTop: '0.3rem' }}>
                            <span style={{ color: '#ef4444' }}>L: ${parseFloat(ticker24h.lowPrice).toLocaleString()}</span>
                        </div>
                    </div>
                )}

                {/* 24h Volume Card */}
                {ticker24h && (
                    <div style={{
                        background: 'white',
                        borderRadius: '12px',
                        padding: '1.5rem',
                        boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
                    }}>
                        <div style={{ fontSize: '0.9rem', color: '#666', marginBottom: '0.5rem' }}>
                            24h Volume
                        </div>
                        <div style={{ fontSize: '1.3rem', fontWeight: 'bold', color: '#333' }}>
                            {parseFloat(ticker24h.volume).toLocaleString(undefined, {maximumFractionDigits: 0})} ETH
                        </div>
                        <div style={{ fontSize: '0.85rem', color: '#999', marginTop: '0.5rem' }}>
                            ${(parseFloat(ticker24h.quoteVolume) / 1000000).toFixed(2)}M
                        </div>
                    </div>
                )}
            </div>

            {/* Price Chart */}
            <div style={{
                background: 'white',
                borderRadius: '12px',
                padding: '1.5rem',
                boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                marginBottom: '2rem'
            }}>
                <h2 style={{ marginTop: 0, marginBottom: '1rem' }}>
                    24 Hour Price Chart
                </h2>
                <div style={{ height: '400px' }}>
                    <Line data={chartData} options={chartOptions} />
                </div>
            </div>

            {/* Portfolio & Trading Section */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginBottom: '2rem' }}>
                {/* Portfolio Card */}
                {portfolio && (
                    <div style={{
                        background: 'white',
                        borderRadius: '12px',
                        padding: '1.5rem',
                        boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
                    }}>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
                            <h2 style={{ marginTop: 0, marginBottom: 0 }}>💼 Portfolio</h2>
                            <span style={{
                                padding: '4px 12px',
                                borderRadius: '12px',
                                fontSize: '0.8rem',
                                fontWeight: 'bold',
                                background: '#fef3cd',
                                color: '#856404'
                            }}>
                                🧪 Testnet
                            </span>
                        </div>
                        <div style={{ marginBottom: '1rem' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                                <span style={{ color: '#666' }}>USDC Balance:</span>
                                <span style={{ fontWeight: 'bold' }}>${safeParseFloat(portfolio.usdcBalance || portfolio.usdBalance).toLocaleString(undefined, {minimumFractionDigits: 2})}</span>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                                <span style={{ color: '#666' }}>ETH Balance:</span>
                                <span style={{ fontWeight: 'bold' }}>{safeParseFloat(portfolio.ethBalance).toFixed(6)} ETH</span>
                            </div>
                            {averageCost && portfolio.ethBalance > 0 && ethPrice && (
                                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem', paddingTop: '0.5rem', borderTop: '1px dashed #eee' }}>
                                    <span style={{ color: '#666' }}>Avg Cost:</span>
                                    <span style={{ fontWeight: 'bold', color: '#f59e0b' }}>${averageCost.toFixed(2)}</span>
                                </div>
                            )}
                            {fees && (
                                <div style={{ gridColumn: '1 / span 2', marginTop: '0.5rem' }}>
                                    <div style={{ color: '#666', fontSize: '0.85rem' }}>Fees & Break-even</div>
                                    {(() => {
                                        const maker = safeParseFloat(fees.maker);
                                        const taker = safeParseFloat(fees.taker);
                                        const entry = (latestRec.entryType === 'LIMIT' && latestRec.entryPrice)
                                            ? safeParseFloat(latestRec.entryPrice)
                                            : (ethPrice ? safeParseFloat(ethPrice.price) : null);
                                        if (!entry || entry <= 0) {
                                            return <div style={{ color: '#888' }}>Entry price unknown. Unable to compute break-even.</div>;
                                        }
                                        const roundTrip = taker * 2; // conservative
                                        const breakEven = entry * (1 + roundTrip);
                                        const tp1 = latestRec.takeProfit1 ? safeParseFloat(latestRec.takeProfit1) : null;
                                        const pctToTp = tp1 ? (tp1 - entry) / entry : null;
                                        const clears = (pctToTp !== null) ? (pctToTp > roundTrip) : null;
                                        return (
                                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem', marginTop: '0.25rem' }}>
                                                <div>
                                                    <div style={{ color: '#888' }}>Entry Used</div>
                                                    <div style={{ fontWeight: 600 }}>${entry.toFixed(2)} {latestRec.entryType === 'LIMIT' && latestRec.entryPrice ? '(limit)' : '(market/current)'}</div>
                                                </div>
                                                <div>
                                                    <div style={{ color: '#888' }}>Break-even Exit</div>
                                                    <div style={{ fontWeight: 600 }}>${breakEven.toFixed(2)}</div>
                                                </div>
                                                <div>
                                                    <div style={{ color: '#888' }}>Round-trip Fees</div>
                                                    <div style={{ fontWeight: 600 }}>{(roundTrip * 100).toFixed(2)}%</div>
                                                </div>
                                                <div>
                                                    <div style={{ color: '#888' }}>TP1 vs Entry</div>
                                                    <div style={{ fontWeight: 600 }}>{pctToTp !== null ? (pctToTp * 100).toFixed(2) + '%' : '—'}</div>
                                                </div>
                                                <div style={{ gridColumn: '1 / span 2', color: clears === null ? '#999' : (clears ? '#10b981' : '#ef4444'), fontWeight: 600 }}>
                                                    {clears === null ? 'No TP1 defined' : (clears ? '✅ TP1 clears fees' : '❌ TP1 does not clear fees')}
                                                </div>
                                                <div style={{ gridColumn: '1 / span 2', fontSize: '0.8rem', color: '#999' }}>
                                                    Assumption: taker + taker (conservative)
                                                </div>
                                            </div>
                                        );
                                    })()}
                                </div>
                            )}
                            {averageCost && portfolio.ethBalance > 0 && ethPrice && (
                                <>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                                        <span style={{ color: '#666' }}>Current P/L:</span>
                                        <span style={{ 
                                            fontWeight: 'bold', 
                                            color: (safeParseFloat(ethPrice.price) - averageCost) >= 0 ? '#10b981' : '#ef4444' 
                                        }}>
                                            {(safeParseFloat(ethPrice.price) - averageCost) >= 0 ? '+' : ''}
                                            ${((safeParseFloat(ethPrice.price) - averageCost) * safeParseFloat(portfolio.ethBalance)).toFixed(2)}
                                            {' '}({(((safeParseFloat(ethPrice.price) / averageCost) - 1) * 100).toFixed(2)}%)
                                        </span>
                                    </div>
                                </>
                            )}
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem', paddingTop: '0.5rem', borderTop: '1px solid #eee' }}>
                                <span style={{ color: '#666' }}>Total Value:</span>
                                <span style={{ fontWeight: 'bold', fontSize: '1.1rem' }}>
                                    ${portfolio.totalValue ? safeParseFloat(portfolio.totalValue).toLocaleString(undefined, {minimumFractionDigits: 2}) : 
                                      (ethPrice ? (safeParseFloat(portfolio.usdcBalance || portfolio.usdBalance) + safeParseFloat(portfolio.ethBalance) * safeParseFloat(ethPrice.price)).toLocaleString(undefined, {minimumFractionDigits: 2}) : '---')}
                                </span>
                            </div>
                        </div>
                        <div style={{ fontSize: '0.85rem', color: '#888' }}>
                            Total Trades: {safeParseFloat(portfolio.totalTrades, 0).toFixed(0)}
                        </div>
                    </div>
                )}

                {/* Latest AI Recommendation (read-only) */}
                <div style={{
                    background: 'white',
                    borderRadius: '12px',
                    padding: '1.5rem',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
                }}>
                    <h2 style={{ marginTop: 0, marginBottom: '1rem' }}>🧠 Latest AI Recommendation</h2>
                    {!latestRec ? (
                        <div style={{ color: '#888' }}>No recommendation yet. The AI will post here when available.</div>
                    ) : (
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                            <div>
                                <div style={{ color: '#666', fontSize: '0.85rem' }}>Signal</div>
                                <div style={{ fontWeight: '600' }}>{latestRec.signal}</div>
                            </div>
                            <div>
                                <div style={{ color: '#666', fontSize: '0.85rem' }}>Confidence</div>
                                <div style={{ fontWeight: '600' }}>{latestRec.confidence}</div>
                            </div>
                            <div>
                                <div style={{ color: '#666', fontSize: '0.85rem' }}>Amount</div>
                                <div style={{ fontWeight: '600' }}>
                                    {latestRec.amount ? (
                                        latestRec.amountType === 'USD'
                                            ? `$${parseFloat(latestRec.amount).toFixed(2)}`
                                            : `${parseFloat(latestRec.amount).toFixed(5)} ETH`
                                    ) : '—'}
                                </div>
                            </div>
                            <div>
                                <div style={{ color: '#666', fontSize: '0.85rem' }}>Entry</div>
                                <div style={{ fontWeight: '600' }}>
                                    {latestRec.entryType || '—'}
                                    {latestRec.entryPrice ? ` @ $${parseFloat(latestRec.entryPrice).toFixed(2)}` : ''}
                                </div>
                            </div>
                            <div>
                                <div style={{ color: '#666', fontSize: '0.85rem' }}>Stop-Loss</div>
                                <div style={{ fontWeight: '600' }}>{latestRec.stopLoss ? `$${parseFloat(latestRec.stopLoss).toFixed(2)}` : '—'}</div>
                            </div>
                            <div>
                                <div style={{ color: '#666', fontSize: '0.85rem' }}>Take-Profit 1</div>
                                <div style={{ fontWeight: '600' }}>{latestRec.takeProfit1 ? `$${parseFloat(latestRec.takeProfit1).toFixed(2)}` : '—'}</div>
                            </div>
                            <div>
                                <div style={{ color: '#666', fontSize: '0.85rem' }}>Take-Profit 2</div>
                                <div style={{ fontWeight: '600' }}>{latestRec.takeProfit2 ? `$${parseFloat(latestRec.takeProfit2).toFixed(2)}` : '—'}</div>
                            </div>
                            <div>
                                <div style={{ color: '#666', fontSize: '0.85rem' }}>Horizon</div>
                                <div style={{ fontWeight: '600' }}>{latestRec.timeHorizonMinutes ? `${latestRec.timeHorizonMinutes} min` : '—'}</div>
                            </div>
                            <div style={{ gridColumn: '1 / span 2', borderTop: '1px solid #eee', paddingTop: '0.75rem' }}>
                                <div style={{ color: '#666', fontSize: '0.85rem' }}>Status</div>
                                <div style={{ fontWeight: '600' }}>
                                    {latestRec.executed
                                        ? '✅ Executed'
                                        : (latestRec.entryOrderStatus ? `⌛ ${latestRec.entryOrderStatus}` : '— Not Executed')}
                                </div>
                                {(latestRec.entryOrderId || latestRec.entryOrderType) && (
                                    <div style={{ marginTop: '0.25rem', fontSize: '0.9rem', color: '#555' }}>
                                        {latestRec.entryOrderId ? `Order #${latestRec.entryOrderId} ` : ''}
                                        {latestRec.entryOrderType ? `(${latestRec.entryOrderType})` : ''}
                                    </div>
                                )}
                            </div>
                            {(latestRec.ocoOrderListId || latestRec.exitOrders) && (
                                <div style={{ gridColumn: '1 / span 2', marginTop: '0.5rem' }}>
                                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                        <div style={{ color: '#666', fontSize: '0.85rem' }}>Exit Orders (OCO)</div>
                                        <button onClick={handleRefreshOcoStatus} style={{ fontSize: '0.8rem', padding: '0.25rem 0.5rem', border: '1px solid #ddd', borderRadius: 4, background: 'white', cursor: 'pointer' }}>Refresh</button>
                                    </div>
                                    <div style={{ fontWeight: '600' }}>{latestRec.ocoOrderListId ? `List #${latestRec.ocoOrderListId}` : '—'}</div>
                                    {latestRec.exitOrders && (
                                        <div style={{ fontSize: '0.9rem', color: '#555', marginTop: '0.25rem', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem' }}>
                                            {(() => {
                                                const items = [];
                                                const eo = latestRec.exitOrders || {};
                                                for (let i = 1; i <= 4; i++) {
                                                    const id = eo[`order${i}Id`];
                                                    if (!id) continue;
                                                    const type = eo[`order${i}Type`];
                                                    const status = eo[`order${i}Status`];
                                                    const label = type === 'STOP_LOSS_LIMIT' ? 'Stop (SL)' : (type === 'LIMIT' ? 'Take Profit (TP)' : (type || 'Order'));
                                                    items.push(
                                                        <div key={`oco-${i}`} style={{ border: '1px solid #eee', borderRadius: 6, padding: '0.5rem', background: '#fafafa' }}>
                                                            <div style={{ fontWeight: 600 }}>{label}</div>
                                                            <div>ID: {id}</div>
                                                            {type && <div>Type: {type}</div>}
                                                            {status && <div>Status: {status}</div>}
                                                        </div>
                                                    );
                                                }
                                                return items.length ? items : <div>—</div>;
                                            })()}
                                        </div>
                                    )}
                                </div>
                            )}
                            {latestRec.reasoning && (
                                <div style={{ gridColumn: '1 / span 2' }}>
                                    <div style={{ color: '#666', fontSize: '0.85rem' }}>Reasoning</div>
                                    <div style={{ whiteSpace: 'pre-wrap' }}>{latestRec.reasoning}</div>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </div>

            {/* Two column layout: Open Orders & Trade History */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginBottom: '2rem' }}>
                {/* Open Orders (left) */}
                <div style={{
                    background: 'white',
                    borderRadius: '12px',
                    padding: '1.5rem',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                    maxHeight: '500px',
                    overflow: 'auto'
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
                        <h2 style={{ marginTop: 0, marginBottom: 0 }}>📋 Open Orders</h2>
                        <span style={{
                            padding: '4px 12px',
                            borderRadius: '12px',
                            fontSize: '0.8rem',
                            fontWeight: 'bold',
                            background: '#fef3cd',
                            color: '#856404'
                        }}>
                            🧪 Testnet
                        </span>
                    </div>
                    {openOrders.length === 0 ? (
                        <p style={{ color: '#888', textAlign: 'center' }}>No open orders.</p>
                    ) : (
                        <div>
                            {openOrders.slice(0, 50).map((order, idx) => {
                                const isExpanded = expandedTrades[idx];
                                const toggleExpand = () => {
                                    setExpandedTrades(prev => ({
                                        ...prev,
                                        [idx]: !prev[idx]
                                    }));
                                };
                                const side = order.side || '—';

                                return (
                                    <div key={idx} style={{
                                        marginBottom: '0.5rem',
                                        background: '#f9fafb',
                                        borderRadius: '6px',
                                        borderLeft: `4px solid ${side === 'BUY' ? '#10b981' : '#ef4444'}`,
                                        overflow: 'hidden'
                                    }}>
                                        {/* Main card - clickable */}
                                        <div 
                                            onClick={toggleExpand}
                                            style={{
                                                padding: '0.75rem',
                                                cursor: 'pointer',
                                                userSelect: 'none'
                                            }}
                                        >
                                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.25rem' }}>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                                                    <span style={{ fontWeight: 'bold', color: side === 'BUY' ? '#10b981' : '#ef4444' }}>
                                                        {side}
                                                    </span>
                                                    <span style={{ fontSize: '0.75rem', color: '#999' }}>
                                                        {isExpanded ? '▼' : '▶'}
                                                    </span>
                                                </div>
                                                <span style={{ fontSize: '0.85rem', color: '#666', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                                                    {(() => { const t = getOrderTime(order); return t ? new Date(t).toLocaleString() : '—'; })()}
                                                    {order.type === 'LIMIT' && (order.status === 'NEW' || order.status === 'PARTIALLY_FILLED') && (() => {
                                                        const info = getExpiryInfoForOrder(order);
                                                        return info ? (
                                                            <span style={{ color: info.remainingMs <= 0 ? '#ef4444' : '#6b7280' }}>
                                                                Expires in {formatCountdown(info.remainingMs)}
                                                            </span>
                                                        ) : null;
                                                    })()}
                                                    <button onClick={(e) => { e.stopPropagation(); handleCancelOrder(order); }} style={{ padding: '0.25rem 0.5rem', borderRadius: 4, border: '1px solid #ef4444', color: '#ef4444', background: 'white', cursor: 'pointer' }}>Cancel</button>
                                                </span>
                                            </div>
                                            <div style={{ fontSize: '0.9rem' }}>
                                                Qty: {safeParseFloat(order.origQty || order.executedQty).toFixed(6)} @ ${safeParseFloat(order.price).toFixed(2)} ({order.type}/{order.timeInForce || 'GTC'}) — {order.status}
                                            </div>
                                        </div>

                                        {/* Expanded details panel */}
                                        {isExpanded && (
                                            <div style={{
                                                padding: '0.75rem',
                                                background: 'white',
                                                borderTop: '1px solid #e5e7eb',
                                                fontSize: '0.85rem'
                                            }}>
                                                <div style={{ fontWeight: 'bold', marginBottom: '0.5rem', color: '#666' }}>
                                                    📊 Order Details
                                                </div>
                                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem' }}>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Order ID:</span>
                                                        <div style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>#{order.orderId}</div>
                                                    </div>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Client ID:</span>
                                                        <div style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{order.clientOrderId}</div>
                                                    </div>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Symbol:</span>
                                                        <div>{order.symbol}</div>
                                                    </div>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Side:</span>
                                                        <div style={{ color: side === 'BUY' ? '#10b981' : '#ef4444' }}>{side}</div>
                                                    </div>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Type:</span>
                                                        <div>{order.type}</div>
                                                    </div>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Time In Force:</span>
                                                        <div>{order.timeInForce || 'GTC'}</div>
                                                    </div>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Price:</span>
                                                        <div>${safeParseFloat(order.price).toFixed(2)}</div>
                                                    </div>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Orig Qty:</span>
                                                        <div>{safeParseFloat(order.origQty).toFixed(6)} ETH</div>
                                                    </div>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Executed Qty:</span>
                                                        <div>{safeParseFloat(order.executedQty).toFixed(6)} ETH</div>
                                                    </div>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Status:</span>
                                                        <div>{order.status}</div>
                                                    </div>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Placed At:</span>
                                                        <div>{(() => {
                                                            const info = getExpiryInfoForOrder(order);
                                                            const t = info?.placedMs || getOrderTime(order);
                                                            return t ? new Date(t).toLocaleString() : '—';
                                                        })()}</div>
                                                    </div>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Horizon:</span>
                                                        <div>{(() => { const info = getExpiryInfoForOrder(order); return info?.horizonMin ? `${info.horizonMin} min` : '—'; })()}</div>
                                                    </div>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Expiry:</span>
                                                        <div>{(() => { const info = getExpiryInfoForOrder(order); return info ? new Date(info.expiryMs).toLocaleString() : '—'; })()}</div>
                                                    </div>
                                                    <div>
                                                        <span style={{ color: '#888' }}>Time Remaining:</span>
                                                        <div>{(() => { const info = getExpiryInfoForOrder(order); return info ? formatCountdown(info.remainingMs) : '—'; })()}</div>
                                                    </div>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>

                {/* Trade History (right) */}
                <div style={{
                    background: 'white',
                    borderRadius: '12px',
                    padding: '1.5rem',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                    maxHeight: '500px',
                    overflow: 'auto'
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
                        <h2 style={{ marginTop: 0, marginBottom: 0 }}>📜 Trade History</h2>
                        <span style={{
                            padding: '4px 12px',
                            borderRadius: '12px',
                            fontSize: '0.8rem',
                            fontWeight: 'bold',
                            background: '#fef3cd',
                            color: '#856404'
                        }}>
                            🧪 Testnet
                        </span>
                    </div>
                    {trades.length === 0 ? (
                        <p style={{ color: '#888', textAlign: 'center' }}>No trades yet.</p>
                    ) : (
                        <div>
                            {[...trades].reverse().slice(0, 50).map((t, i) => {
                                const kind = t.isBuyer ? 'BUY' : 'SELL';
                                const isOcoSell = !t.isBuyer && (t.orderListId !== null && t.orderListId !== undefined);
                                const leg = isOcoSell ? (orderTypeById[t.orderId] || null) : null;
                                // For BUY trades, see if an OCO was created for this entry order
                                const ocoMeta = t.isBuyer ? entryOcoByOrderId[t.orderId] : null;
                                const ocoStatus = ocoMeta ? summarizeOcoStatus(ocoMeta.exitOrders) : null;
                                const ocoTooltip = ocoMeta && ocoMeta.exitOrders
                                    ? Object.keys(ocoMeta.exitOrders)
                                        .filter(k => k.toLowerCase().includes('order') && k.toLowerCase().includes('status'))
                                        .map(k => `${k}: ${ocoMeta.exitOrders[k]}`)
                                        .join(' | ')
                                    : '';
                                return (
                                    <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0', borderTop: i === 0 ? 'none' : '1px solid #eee' }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                                            <span style={{ fontWeight: 600, color: t.isBuyer ? '#10b981' : '#ef4444' }}>{kind}</span>
                                            <span style={{ color: '#666' }}>{safeParseFloat(t.quantity || t.qty || t.executedQty).toFixed(6)} ETH @ ${safeParseFloat(t.price).toFixed(2)}</span>
                                            {/* SELLs that belong to OCO */}
                                            {isOcoSell && (
                                                <span style={{ padding: '2px 8px', borderRadius: '10px', fontSize: '0.7rem', fontWeight: 600, border: '1px solid #16a34a', background: '#dcfce7', color: '#166534' }}>
                                                    {leg === 'TP' ? 'OCO TP Fill' : leg === 'SL' ? 'OCO SL Fill' : 'OCO Fill'}
                                                </span>
                                            )}
                                            {/* BUYs with attached OCO */}
                                            {t.isBuyer && ocoMeta && (
                                                <span style={ocoBadgeStyles(ocoStatus)} title={`OCO #${ocoMeta.ocoOrderListId}${ocoTooltip ? ' — ' + ocoTooltip : ''}`}>
                                                    OCO: {ocoStatus}
                                                </span>
                                            )}
                                            {/* Order meta: type/status/maker-taker */}
                                            {(() => {
                                                const meta = orderMetaById[t.orderId] || {};
                                                const typeLabel = meta.type || (t.isMaker ? 'LIMIT?' : 'MARKET?');
                                                const tif = meta.timeInForce ? `/${meta.timeInForce}` : '';
                                                const tooltip = buildOrderTooltip(meta, t);
                                                return (
                                                    <>
                                                        <span style={{ padding: '2px 8px', borderRadius: '10px', fontSize: '0.7rem', fontWeight: 600, border: '1px solid #93c5fd', background: '#eff6ff', color: '#1d4ed8' }}
                                                            title={tooltip}>
                                                            {typeLabel}{tif && ` ${tif}`}
                                                        </span>
                                                        {meta.status && (
                                                            <span style={statusBadgeStyles(meta.status)} title={tooltip}>
                                                                {meta.status}
                                                            </span>
                                                        )}
                                                        <span style={{ padding: '2px 8px', borderRadius: '10px', fontSize: '0.7rem', fontWeight: 600, border: '1px solid #fde68a', background: '#fffbeb', color: '#92400e' }}
                                                            title={t.isMaker ? 'Maker (limit resting on book)' : 'Taker (market/marketable)'}>
                                                            {t.isMaker ? 'MAKER' : 'TAKER'}
                                                        </span>
                                                    </>
                                                );
                                            })()}
                                        </div>
                                        <div style={{ color: '#666' }}>{t.time ? new Date(t.time).toLocaleString() : '—'}</div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </div>

            {/* AI Trading Chat - full width row */}
            <div style={{
                background: 'white',
                borderRadius: '12px',
                padding: '1.5rem',
                boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                display: 'flex',
                flexDirection: 'column',
                marginBottom: '2rem'
            }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                    <h2 style={{ margin: 0 }}>🤖 AI Trading Advisor</h2>
                    <button
                        onClick={handleAnalyzeMarket}
                        disabled={chatLoading}
                        style={{
                            padding: '0.5rem 1rem',
                            background: '#10b981',
                            color: 'white',
                            border: 'none',
                            borderRadius: '6px',
                            cursor: chatLoading ? 'not-allowed' : 'pointer',
                            fontWeight: '600',
                            fontSize: '0.85rem',
                            opacity: chatLoading ? 0.6 : 1,
                            display: 'flex',
                            alignItems: 'center',
                            gap: '0.3rem'
                        }}
                        title="Get AI market analysis and trading recommendation"
                    >
                        📊 Analyze Market
                    </button>
                </div>

                {/* Chat Messages */}
                <div style={{
                    maxHeight: '400px',
                    overflowY: 'auto',
                    marginBottom: '1rem',
                    padding: '0.5rem',
                    background: '#f9fafb',
                    borderRadius: '6px'
                }}>
                    {chatMessages.length === 0 ? (
                        <div style={{ color: '#888', textAlign: 'center', padding: '2rem' }}>
                            <div style={{ marginBottom: '1rem' }}>
                                Ask me about market conditions, trading strategies, or request trade recommendations!
                            </div>
                            <div style={{ fontSize: '0.9rem', color: '#999' }}>
                                💡 Tip: Click "Analyze Market" for instant analysis
                            </div>
                        </div>
                    ) : (
                        chatMessages.map((msg, idx) => (
                            <div key={idx} style={{
                                marginBottom: '0.75rem',
                                padding: '0.75rem',
                                background: msg.role === 'user' ? '#667eea' : 'white',
                                color: msg.role === 'user' ? 'white' : '#333',
                                borderRadius: '8px',
                                marginLeft: msg.role === 'user' ? '2rem' : '0',
                                marginRight: msg.role === 'user' ? '0' : '2rem',
                                boxShadow: '0 1px 3px rgba(0,0,0,0.1)'
                            }}>
                                <div style={{ fontSize: '0.8rem', marginBottom: '0.25rem', opacity: 0.7 }}>
                                    {msg.role === 'user' ? 'You' : 'AI Advisor'}
                                </div>
                                <div style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</div>
                            </div>
                        ))
                    )}
                    {chatLoading && (
                        <div style={{ textAlign: 'center', color: '#888' }}>
                            <span>AI is thinking...</span>
                        </div>
                    )}
                    <div ref={chatEndRef} />
                </div>

                {/* Chat Input */}
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <input
                        type="text"
                        value={chatInput}
                        onChange={(e) => setChatInput(e.target.value)}
                        onKeyPress={(e) => e.key === 'Enter' && handleSendChat()}
                        placeholder="Ask AI for trading advice..."
                        style={{
                            flex: 1,
                            padding: '0.75rem',
                            border: '1px solid #ddd',
                            borderRadius: '6px'
                        }}
                        disabled={chatLoading}
                    />
                    <button
                        onClick={handleSendChat}
                        disabled={chatLoading}
                        style={{
                            padding: '0.75rem 1.5rem',
                            background: '#667eea',
                            color: 'white',
                            border: 'none',
                            borderRadius: '6px',
                            cursor: chatLoading ? 'not-allowed' : 'pointer',
                            fontWeight: '600',
                            opacity: chatLoading ? 0.6 : 1
                        }}
                    >
                        Send
                    </button>
                </div>
            </div>
        </div>
    );
}

export default EthTrading;

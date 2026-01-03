import React, { useEffect, useState } from 'react';
import './RecommendationHistory.css';

/**
 * Recommendation History Component
 * Displays AI trading recommendations with memory evolution
 */
function RecommendationHistory() {
    console.log('RecommendationHistory component rendering...');
    
    const [recommendations, setRecommendations] = useState([]);
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [limit, setLimit] = useState(20);
    const [filter, setFilter] = useState('all'); // all, executed, buy, sell, hold
    const [expandedRows, setExpandedRows] = useState({});

    useEffect(() => {
        console.log('RecommendationHistory mounted, fetching data...');
        console.log('Token:', sessionStorage.getItem("token") ? 'Present' : 'Missing');
        fetchRecommendations();
        fetchStats();
    }, [limit, filter]);

    const fetchRecommendations = async () => {
        setLoading(true);
        console.log('fetchRecommendations called with limit:', limit, 'filter:', filter);
        
        try {
            let url = `/api/trading/recommendations?limit=${limit}`;
            
            if (filter === 'executed') {
                url += '&executed=true';
            } else if (filter !== 'all') {
                url += `&signal=${filter.toUpperCase()}`;
            }

            console.log('Fetching from URL:', url);

            const response = await fetch(url, {
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token")
                }
            });

            console.log('Response status:', response.status);
            console.log('Response ok:', response.ok);

            if (response.ok) {
                const data = await response.json();
                console.log('Fetched recommendations - Count:', data.length);
                console.log('First recommendation:', data[0]);
                console.log('Raw data structure:', JSON.stringify(data[0], null, 2));
                setRecommendations(data);
                setError(null);
            } else {
                const errorText = await response.text();
                console.error('Failed to fetch recommendations:', response.status, errorText);
                setError(`Failed to fetch recommendations: ${response.status} - ${errorText}`);
            }
        } catch (err) {
            console.error('Error fetching recommendations:', err);
            console.error('Error stack:', err.stack);
            setError(`Error: ${err.message}`);
        } finally {
            setLoading(false);
        }
    };

    const fetchStats = async () => {
        try {
            const response = await fetch('/api/trading/recommendations/stats', {
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token")
                }
            });

            if (response.ok) {
                const data = await response.json();
                setStats(data);
            }
        } catch (err) {
            console.error('Error fetching stats:', err);
        }
    };

    const formatTimestamp = (timestamp) => {
        if (!timestamp) return 'N/A';
        try {
            const date = new Date(timestamp);
            if (isNaN(date.getTime())) return 'Invalid date';
            
            const now = new Date();
            const diffMs = now - date;
            const diffMins = Math.floor(diffMs / 60000);
            const diffHours = Math.floor(diffMs / 3600000);
            const diffDays = Math.floor(diffMs / 86400000);

            if (diffMins < 1) {
                return 'Just now';
            } else if (diffMins < 60) {
                return `${diffMins}m ago`;
            } else if (diffHours < 24) {
                return `${diffHours}h ago`;
            } else {
                return `${diffDays}d ago`;
            }
        } catch (e) {
            console.error('Error formatting timestamp:', e);
            return 'Invalid date';
        }
    };

    const formatDateTime = (timestamp) => {
        if (!timestamp) return 'N/A';
        try {
            const date = new Date(timestamp);
            if (isNaN(date.getTime())) return 'Invalid date';
            
            return date.toLocaleString('en-US', {
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (e) {
            console.error('Error formatting datetime:', e);
            return 'Invalid date';
        }
    };

    const getSignalColor = (signal) => {
        switch (signal) {
            case 'BUY': return '#10b981'; // green
            case 'SELL': return '#ef4444'; // red
            case 'HOLD': return '#f59e0b'; // amber
            default: return '#6b7280'; // gray
        }
    };

    const getSignalEmoji = (signal) => {
        switch (signal) {
            case 'BUY': return '📈';
            case 'SELL': return '📉';
            case 'HOLD': return '⏸️';
            default: return '❓';
        }
    };

    const getConfidenceEmoji = (confidence) => {
        switch (confidence) {
            case 'HIGH': return '🔥';
            case 'MEDIUM': return '✅';
            case 'LOW': return '⚠️';
            default: return '❓';
        }
    };

    const toggleRow = (id) => {
        setExpandedRows(prev => ({
            ...prev,
            [id]: !prev[id]
        }));
    };

    if (loading && recommendations.length === 0) {
        return (
            <div className="recommendation-history">
                <div className="loading">Loading recommendations...</div>
            </div>
        );
    }

    return (
        <div className="recommendation-history">
            <div className="header">
                <h2>🧠 AI Recommendation History</h2>
                <p className="subtitle">Track AI's memory evolution and decision patterns</p>
            </div>

            {/*/!* Debug Info *!/*/}
            {/*<div style={{ background: '#f0f0f0', padding: '10px', marginBottom: '10px', fontSize: '12px', fontFamily: 'monospace' }}>*/}
            {/*    <strong>Debug Info:</strong><br/>*/}
            {/*    Loading: {loading ? 'Yes' : 'No'}<br/>*/}
            {/*    Recommendations Count: {recommendations.length}<br/>*/}
            {/*    Stats: {stats ? 'Loaded' : 'Not loaded'}<br/>*/}
            {/*    Error: {error || 'None'}<br/>*/}
            {/*    Token: {sessionStorage.getItem("token") ? 'Present' : 'Missing'}<br/>*/}
            {/*    <button onClick={() => console.log('Current state:', {recommendations, stats, error})}>Log State</button>*/}
            {/*</div>*/}

            {error && (
                <div className="error-banner">
                    ⚠️ {error}
                    <br/>
                    <small>Check browser console (F12) for details</small>
                </div>
            )}

            {/* Stats Cards */}
            {stats && (
                <div className="stats-grid">
                    <div className="stat-card">
                        <div className="stat-value">{stats.total}</div>
                        <div className="stat-label">Total Recommendations</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-value">{stats.executed}</div>
                        <div className="stat-label">Executed</div>
                        <div className="stat-sublabel">{stats.executionRate}</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-value">{stats.signals.BUY}</div>
                        <div className="stat-label">📈 BUY Signals</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-value">{stats.signals.SELL}</div>
                        <div className="stat-label">📉 SELL Signals</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-value">{stats.signals.HOLD}</div>
                        <div className="stat-label">⏸️ HOLD Signals</div>
                    </div>
                </div>
            )}

            {/* Filters */}
            <div className="filters">
                <div className="filter-group">
                    <label>Show:</label>
                    <select value={limit} onChange={(e) => setLimit(Number(e.target.value))}>
                        <option value={10}>Last 10</option>
                        <option value={20}>Last 20</option>
                        <option value={50}>Last 50</option>
                        <option value={100}>Last 100</option>
                    </select>
                </div>

                <div className="filter-group">
                    <label>Filter:</label>
                    <div className="filter-buttons">
                        <button 
                            className={filter === 'all' ? 'active' : ''} 
                            onClick={() => setFilter('all')}
                        >
                            All
                        </button>
                        <button 
                            className={filter === 'executed' ? 'active' : ''} 
                            onClick={() => setFilter('executed')}
                        >
                            ✅ Executed
                        </button>
                        <button 
                            className={filter === 'buy' ? 'active' : ''} 
                            onClick={() => setFilter('buy')}
                        >
                            📈 BUY
                        </button>
                        <button 
                            className={filter === 'sell' ? 'active' : ''} 
                            onClick={() => setFilter('sell')}
                        >
                            📉 SELL
                        </button>
                        <button 
                            className={filter === 'hold' ? 'active' : ''} 
                            onClick={() => setFilter('hold')}
                        >
                            ⏸️ HOLD
                        </button>
                    </div>
                </div>

                <button className="refresh-button" onClick={fetchRecommendations}>
                    🔄 Refresh
                </button>
            </div>

            {/* Recommendations Table */}
            {recommendations.length === 0 ? (
                <div className="empty-state">
                    <p>📊 No recommendations found</p>
                    <p className="hint">Generate one with the AI trading bot</p>
                </div>
            ) : (
                <div className="recommendations-table">
                    <table>
                        <thead>
                            <tr>
                                <th>Time</th>
                                <th>Signal</th>
                                <th>Confidence</th>
                                <th>Amount</th>
                                <th>Status</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {recommendations.map((rec) => (
                                <React.Fragment key={rec.id}>
                                    <tr className={expandedRows[rec.id] ? 'expanded' : ''}>
                                        <td className="time-cell">
                                            <div className="time-primary">{formatTimestamp(rec.timestamp)}</div>
                                            <div className="time-secondary">{formatDateTime(rec.timestamp)}</div>
                                        </td>
                                        <td>
                                            <span 
                                                className="signal-badge" 
                                                style={{ backgroundColor: getSignalColor(rec.signal) }}
                                            >
                                                {getSignalEmoji(rec.signal)} {rec.signal}
                                            </span>
                                        </td>
                                        <td>
                                            <span className={`confidence-badge confidence-${rec.confidence.toLowerCase()}`}>
                                                {getConfidenceEmoji(rec.confidence)} {rec.confidence}
                                            </span>
                                        </td>
                                        <td className="amount-cell">
                                            {rec.amount && !isNaN(parseFloat(rec.amount)) ? (
                                                rec.amountType === 'USD' 
                                                    ? `$${parseFloat(rec.amount).toFixed(2)}`
                                                    : `${parseFloat(rec.amount).toFixed(4)} ETH`
                                            ) : '—'}
                                        </td>
                                        <td>
                                            {rec.executed ? (
                                                <span className="status-badge executed">✅ Executed</span>
                                            ) : (
                                                <span className="status-badge not-executed">— Not Executed</span>
                                            )}
                                        </td>
                                        <td>
                                            <button 
                                                className="expand-button"
                                                onClick={() => toggleRow(rec.id)}
                                            >
                                                {expandedRows[rec.id] ? '▼ Hide' : '▶ Details'}
                                            </button>
                                        </td>
                                    </tr>
                                    {expandedRows[rec.id] && (
                                        <tr className="details-row">
                                            <td colSpan="6">
                                                <div className="details-content">
                                                    {/* Reasoning */}
                                                    <div className="detail-section">
                                                        <h4>💡 Reasoning</h4>
                                                        <p>{rec.reasoning}</p>
                                                    </div>

                                                    {/* Trade Plan (AI) */}
                                                    <div className="detail-section">
                                                        <h4>📐 Trade Plan (AI)</h4>
                                                        <div className="execution-grid">
                                                            <div>
                                                                <strong>Entry:</strong>
                                                                <div>
                                                                    {rec.entryType || '—'}
                                                                    {rec.entryPrice ? ` @ $${parseFloat(rec.entryPrice).toFixed(2)}` : ''}
                                                                </div>
                                                            </div>
                                                            <div>
                                                                <strong>Stop-Loss:</strong>
                                                                <div>{rec.stopLoss ? `$${parseFloat(rec.stopLoss).toFixed(2)}` : '—'}</div>
                                                            </div>
                                                            <div>
                                                                <strong>Take-Profit 1:</strong>
                                                                <div>{rec.takeProfit1 ? `$${parseFloat(rec.takeProfit1).toFixed(2)}` : '—'}</div>
                                                            </div>
                                                            <div>
                                                                <strong>Take-Profit 2:</strong>
                                                                <div>{rec.takeProfit2 ? `$${parseFloat(rec.takeProfit2).toFixed(2)}` : '—'}</div>
                                                            </div>
                                                            <div>
                                                                <strong>Horizon:</strong>
                                                                <div>{rec.timeHorizonMinutes ? `${rec.timeHorizonMinutes} min` : '—'}</div>
                                                            </div>
                                                        </div>
                                                        <div style={{ marginTop: '0.5rem', paddingTop: '0.5rem', borderTop: '1px solid #e5e7eb' }}>
                                                            <strong>Status:</strong>{' '}
                                                            {rec.executed ? '✅ Executed' : (rec.entryOrderStatus ? `⌛ ${rec.entryOrderStatus}` : '— Not Executed')}
                                                            {(rec.entryOrderId || rec.entryOrderType) && (
                                                                <div style={{ marginTop: '0.25rem', fontSize: '0.9rem', color: '#555' }}>
                                                                    {rec.entryOrderId ? `Order #${rec.entryOrderId} ` : ''}
                                                                    {rec.entryOrderType ? `(${rec.entryOrderType})` : ''}
                                                                </div>
                                                            )}
                                                        </div>
                                                    </div>

                                                    {/* AI Memory */}
                                                    {(() => {
                                                        // Parse aiMemory if it's a string
                                                        let memory = rec.aiMemory;
                                                        if (typeof memory === 'string') {
                                                            try {
                                                                memory = JSON.parse(memory);
                                                            } catch (e) {
                                                                console.error('Failed to parse aiMemory:', e);
                                                                return null;
                                                            }
                                                        }
                                                        
                                                        if (!memory || !Array.isArray(memory) || memory.length === 0) {
                                                            return null;
                                                        }
                                                        
                                                        return (
                                                            <div className="detail-section memory-section">
                                                                <h4>🧠 AI Working Memory</h4>
                                                                <ul className="memory-list">
                                                                    {memory.map((item, idx) => (
                                                                        <li key={idx}>
                                                                            <span className="memory-bullet">•</span>
                                                                            {item}
                                                                        </li>
                                                                    ))}
                                                                </ul>
                                                            </div>
                                                        );
                                                    })()}

                                                    {/* Execution Details */}
                                                    {rec.executed && rec.executionResult && (() => {
                                                        // Parse executionResult if it's a string
                                                        let execResult = rec.executionResult;
                                                        if (typeof execResult === 'string') {
                                                            try {
                                                                execResult = JSON.parse(execResult);
                                                            } catch (e) {
                                                                console.error('Failed to parse executionResult:', e);
                                                                return null;
                                                            }
                                                        }
                                                        
                                                        return (
                                                            <div className="detail-section execution-section">
                                                                <h4>📋 Execution Details</h4>
                                                                <div className="execution-grid">
                                                                    <div>
                                                                        <strong>Order ID:</strong> {execResult.orderId || 'N/A'}
                                                                    </div>
                                                                    <div>
                                                                        <strong>Executed Qty:</strong> {execResult.executedQty || 'N/A'}
                                                                    </div>
                                                                    <div>
                                                                        <strong>Avg Price:</strong> ${execResult.avgPrice || 'N/A'}
                                                                    </div>
                                                                    <div>
                                                                        <strong>Status:</strong> {execResult.status || 'N/A'}
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        );
                                                    })()}
                                                </div>
                                            </td>
                                        </tr>
                                    )}
                                </React.Fragment>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}

export default RecommendationHistory;

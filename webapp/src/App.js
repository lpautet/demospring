import logo from './logo.svg';
import './App.css';
import {useEffect, useState, useCallback} from "react";
import Chat from './Chat';
import EthTrading from './EthTrading';
import RecommendationHistory from './RecommendationHistory';
import {
    Chart as ChartJS,
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    BarElement,
    BarController,
    Title,
    Tooltip,
    Legend,
    TimeScale,
    Filler
} from 'chart.js';
import {Line, Bar} from 'react-chartjs-2';
import 'chartjs-adapter-date-fns';

ChartJS.register(
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    BarElement,
    BarController,
    Title,
    Tooltip,
    Legend,
    TimeScale,
    Filler
);

// Map rf_status to 0-4 bars (adjust thresholds to match your data semantics)
function toSignalBars(rf_status) {
    if (rf_status == null) return 0;
    // Example: higher value = better signal
    if (rf_status >= 80) return 4;
    if (rf_status >= 60) return 3;
    if (rf_status >= 40) return 2;
    if (rf_status >= 20) return 1;
    return 0;
}

function toBatteryPercent({battery_percent, battery_vp}) {
    if (battery_percent != null) return battery_percent;
    if (battery_vp == null) return null;
    // Map 3.2V -> 0%, 4.2V -> 100% (approx; tune for Netatmo)
    const pct = Math.max(0, Math.min(100, Math.round((battery_vp - 3.2) / (4.2 - 3.2) * 100)));
    return pct;
}

function wifiDbmToBars(dBm) {
    if (dBm == null) return 0;
    // Typical Wi‑Fi RSSI mapping: higher (less negative) is better
    if (dBm >= -55) return 4;
    if (dBm >= -65) return 3;
    if (dBm >= -75) return 2;
    if (dBm >= -85) return 1;
    return 0;
}

function getSignalInfo(data) {
    if (!data) return { level: 0, label: 'Signal strength: N/A' };
    // Prefer numeric strengths if available (0-100)
    if (data.rf_strength != null) {
        const lvl = toSignalBars(Number(data.rf_strength));
        const state = data.rf_state != null ? ` (${data.rf_state})` : '';
        return { level: lvl, label: `Signal (RF): ${data.rf_strength}${state}` };
    }
    if (data.wifi_strength != null) {
        const lvl = toSignalBars(Number(data.wifi_strength));
        const state = data.wifi_state != null ? ` (${data.wifi_state})` : '';
        return { level: lvl, label: `Signal (Wi‑Fi): ${data.wifi_strength}${state}` };
    }
    // Legacy fields
    if (data.rf_status != null) {
        return { level: toSignalBars(data.rf_status), label: `Signal (RF): ${data.rf_status}` };
    }
    if (data.wifi_status != null) {
        return { level: wifiDbmToBars(data.wifi_status), label: `Signal (Wi‑Fi): ${data.wifi_status} dBm` };
    }
    // Fallback: check meta container
    const meta = data.meta || {};
    if (meta.rf_strength != null) {
        const lvl = toSignalBars(Number(meta.rf_strength));
        const state = meta.rf_state != null ? ` (${meta.rf_state})` : '';
        return { level: lvl, label: `Signal (RF): ${meta.rf_strength}${state}` };
    }
    if (meta.wifi_strength != null) {
        const lvl = toSignalBars(Number(meta.wifi_strength));
        const state = meta.wifi_state != null ? ` (${meta.wifi_state})` : '';
        return { level: lvl, label: `Signal (Wi‑Fi): ${meta.wifi_strength}${state}` };
    }
    if (meta.rf_status != null) {
        return { level: toSignalBars(meta.rf_status), label: `Signal (RF): ${meta.rf_status}` };
    }
    if (meta.wifi_status != null) {
        return { level: wifiDbmToBars(meta.wifi_status), label: `Signal (Wi‑Fi): ${meta.wifi_status} dBm` };
    }
    return { level: 0, label: 'Signal strength: N/A' };
}

function getBatteryInfo(data) {
    if (!data) return null;
    // Prefer explicit percent
    const percentDirect = data.battery_percent ?? data.meta?.battery_percent;
    if (percentDirect != null) {
        const voltage = data.battery_vp ?? data.meta?.battery_vp;
        const label = voltage != null ? `Battery: ${percentDirect}% (${voltage}V)` : `Battery: ${percentDirect}%`;
        return { percent: percentDirect, label };
    }
    // Map battery_state to an approximate percent if present
    const state = (data.battery_state ?? data.meta?.battery_state) || null;
    const levelMv = data.battery_level ?? data.meta?.battery_level;
    if (state) {
        const map = { full: 100, high: 75, medium: 50, low: 25 };
        const p = map[String(state).toLowerCase()] ?? 0;
        const label = levelMv != null ? `Battery: ${p}% (${state}, ${levelMv})` : `Battery: ${p}% (${state})`;
        return { percent: p, label };
    }
    // If only battery_level available, just display value and use 0% fill
    if (levelMv != null) {
        return { percent: 0, label: `Battery level: ${levelMv}` };
    }
    // Try voltage mapping if available
    const percentFromV = toBatteryPercent({
        battery_percent: undefined,
        battery_vp: data.battery_vp ?? data.meta?.battery_vp
    });
    if (percentFromV != null) {
        const voltage = data.battery_vp ?? data.meta?.battery_vp;
        const label = `Battery: ${percentFromV}% (${voltage}V)`;
        return { percent: percentFromV, label };
    }
    return null;
}

function SignalIcon({level = 0, titleText = "Signal"}) {
    const bars = [0, 1, 2, 3];
    return (
        <span title={titleText} style={{display: 'inline-flex'}}>
            <svg width="20" height="12" viewBox="0 0 20 12" aria-label="Signal" style={{marginLeft: 6}}>
                <title>{titleText}</title>
                {bars.map((b, i) => (
                    <rect key={i}
                          x={i * 5} y={12 - (i + 1) * 3} width="3" height={(i + 1) * 3}
                          fill={i < level ? "#2E7D32" : "#C8E6C9"} rx="1"/>
                ))}
            </svg>
        </span>
    );
}

function BatteryIcon({percent = 0, titleText = "Battery"}) {
    const p = Math.max(0, Math.min(100, percent));
    return (
        <span title={titleText} style={{display: 'inline-flex'}}>
            <svg width="28" height="14" viewBox="0 0 28 14" aria-label="Battery" style={{marginLeft: 6}}>
                <title>{titleText}</title>
                <rect x="1" y="3" width="24" height="8" fill="none" stroke="#555" rx="2"/>
                <rect x="25" y="5" width="2" height="4" fill="#555" rx="1"/>
                <rect x="2" y="4" width={(p / 100) * 22} height="6"
                      fill={p < 20 ? "#C62828" : p < 50 ? "#F9A825" : "#2E7D32"} rx="1"/>
            </svg>
        </span>
    );
}

const MeasurementCard = ({title, data, measures, time}) => {
    if (!measures) {
        return;
    }
    const signal = getSignalInfo(data);
    const battery = getBatteryInfo(data);
    const options = {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: {
                position: 'top',
            },
            title: {
                display: false
            }
        },
        scales: {
            x: {
                type: 'time',
                time: {
                    unit: 'hour',
                    displayFormats: {
                        hour: 'HH:mm'
                    }
                },
                title: {
                    display: false
                }
            }
        }
    };

    // Add scales only for measures that are present in the data
    if (measures?.some(m => m.temperature !== undefined)) {
        options.scales.temperature = {
            type: 'linear',
            display: true,
            position: 'left',
            title: {
                display: true,
                text: 'Temperature (°C)'
            },
            grid: {
                drawOnChartArea: false
            }
        };
    }

    if (measures?.some(m => m.humidity !== undefined)) {
        options.scales.humidity = {
            type: 'linear',
            display: true,
            position: 'right',
            title: {
                display: true,
                text: 'Humidity (%)'
            },
            grid: {
                drawOnChartArea: false
            }
        };
    }

    if (measures?.some(m => m.co2 !== undefined)) {
        options.scales.co2 = {
            type: 'linear',
            display: true,
            position: 'right',
            title: {
                display: true,
                text: 'CO2 (ppm)'
            },
            grid: {
                drawOnChartArea: false
            }
        };
    }

    if (measures?.some(m => m.noise !== undefined)) {
        options.scales.noise = {
            type: 'linear',
            display: true,
            position: 'right',
            title: {
                display: true,
                text: 'Noise (dB)'
            },
            grid: {
                drawOnChartArea: false
            }
        };
    }

    if (measures?.some(m => m.sp_temperature !== undefined)) {
        options.scales.temperature = {
            type: 'linear',
            display: true,
            position: 'left',
            title: {
                display: true,
                text: 'Temperature (°C)'
            },
            grid: {
                drawOnChartArea: false
            }
        };
    }

    if (measures?.some(m => m.sum_boiler_on !== undefined)) {
        options.scales.sum_boiler_on = {
            type: 'linear',
            display: true,
            position: 'right',
            title: {
                display: true,
                text: 'Boiler On (min)'
            },
            grid: {
                drawOnChartArea: false
            }
        };
    }

    if (measures?.some(m => m.rain !== undefined)) {
        options.scales.rain = {
            type: 'linear',
            display: true,
            position: 'right',
            title: {
                display: true,
                text: 'Rain (mm)'
            },
            grid: {
                drawOnChartArea: false
            }
        };
    }

    if (measures?.some(m => m.sum_rain !== undefined)) {
        options.scales.rain = {
            type: 'linear',
            display: true,
            position: 'right',
            title: {
                display: true,
                text: 'Rain (mm)'
            },
            grid: {
                drawOnChartArea: false
            }
        };
    }

    const chartData = {
        labels: measures?.map(m => new Date(m.timestamp * 1000)) || [],
        datasets: []
    };

    // Add datasets only for measures that are present in the data
    if (measures?.some(m => m.temperature !== undefined)) {
        chartData.datasets.push({
            label: 'Temperature',
            data: measures?.map(m => m.temperature) || [],
            borderColor: 'rgb(255, 99, 132)',
            backgroundColor: 'rgba(255, 99, 132, 0.5)',
            yAxisID: 'temperature',
            pointRadius: 0,
            borderWidth: 2
        });
    }

    if (measures?.some(m => m.humidity !== undefined)) {
        chartData.datasets.push({
            label: 'Humidity',
            data: measures?.map(m => m.humidity) || [],
            borderColor: 'rgb(53, 162, 235)',
            backgroundColor: 'rgba(53, 162, 235, 0.5)',
            yAxisID: 'humidity',
            pointRadius: 0,
            borderWidth: 2
        });
    }

    if (measures?.some(m => m.co2 !== undefined)) {
        chartData.datasets.push({
            label: 'CO2',
            data: measures?.map(m => m.co2) || [],
            borderColor: 'rgb(75, 192, 192)',
            backgroundColor: 'rgba(75, 192, 192, 0.5)',
            yAxisID: 'co2',
            pointRadius: 0,
            borderWidth: 2
        });
    }

    if (measures?.some(m => m.noise !== undefined)) {
        chartData.datasets.push({
            label: 'Noise',
            data: measures?.map(m => m.noise) || [],
            borderColor: 'rgb(255, 159, 64)',
            backgroundColor: 'rgba(255, 159, 64, 0.5)',
            yAxisID: 'noise',
            pointRadius: 0,
            borderWidth: 2
        });
    }

    if (measures?.some(m => m.sp_temperature !== undefined)) {
        chartData.datasets.push({
            label: 'Setpoint',
            data: measures?.map(m => m.sp_temperature) || [],
            borderColor: 'rgb(153, 102, 255)',
            backgroundColor: 'rgba(153, 102, 255, 0.5)',
            yAxisID: 'temperature',
            pointRadius: 0,
            borderWidth: 2
        });
    }

    if (measures?.some(m => m.sum_boiler_on !== undefined)) {
        chartData.datasets.push({
            label: 'Boiler On',
            data: measures?.map(m => m.sum_boiler_on) || [],
            borderColor: 'rgb(255, 140, 0)',  // Dark orange for border
            backgroundColor: 'rgba(255, 69, 0, 0.5)',  // Orange-red for bars
            yAxisID: 'sum_boiler_on',
            type: 'bar',  // Specify this dataset as bars
            barPercentage: 0.8,  // Make bars slightly thinner
            categoryPercentage: 0.8,  // Make bars slightly thinner
            borderWidth: 1
        });
    }

    if (measures?.some(m => m.rain !== undefined)) {
        chartData.datasets.push({
            label: 'Rain',
            data: measures?.map(m => m.rain) || [],
            borderColor: 'rgb(75, 192, 192)',
            backgroundColor: 'rgba(75, 192, 192, 0.5)',
            yAxisID: 'rain',
            pointRadius: 0,
            borderWidth: 2
        });
    }

    if (measures?.some(m => m.sum_rain !== undefined)) {
        chartData.datasets.push({
            label: 'Rain Total',
            data: measures?.map(m => m.sum_rain) || [],
            borderColor: 'rgb(153, 102, 255)',
            backgroundColor: 'rgba(153, 102, 255, 0.5)',
            yAxisID: 'rain',  // Use the same axis as rain
            type: 'bar',
            barPercentage: 0.8,
            categoryPercentage: 0.8,
            borderWidth: 1
        });
    }

    return (
        <div className="card">
            <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
                <p style={{margin: 0}}>{title}</p>
                <div style={{display: 'flex', alignItems: 'center'}}>
                    <SignalIcon level={signal.level} titleText={signal.label}/>
                    {battery && <BatteryIcon percent={battery.percent} titleText={battery.label}/>}                
                </div>
            </div>
            <div style={{display: 'flex', flexDirection: 'row', flex: 1, minHeight: 0}}>
                <div className="measurements" style={{flex: '0 0 auto', paddingRight: '1em'}}>
                    {data.temperature !== undefined && <p className="temperature">{data.temperature}&deg;</p>}
                    {data.humidity !== undefined && <p>{data.humidity}%</p>}
                    {data.co2 !== undefined && <p>CO2 {data.co2}ppm</p>}
                    {data.noise !== undefined && <p>noise {data.noise}dB</p>}
                    {data.rain !== undefined && <p>{data.rain}mm/h</p>}
                    {data.sum_rain !== undefined && <p>last 1h: {data.sum_rain}mm</p>}
                    {data.sum_rain_24 !== undefined && <p>Today: {data.sum_rain_24}mm</p>}
                    {data.therm_measured_temperature !== undefined &&
                        <p className="temperature">{data.therm_measured_temperature}&deg;</p>}
                    {data.therm_setpoint_temperature !== undefined &&
                        <p className={"settemperature"}>{data.therm_setpoint_temperature}&deg;</p>}
                    {data.boiler_status !== undefined && <p>Heating: {data.boiler_status ? "ON" : "OFF"}</p>}
                    {data.boiler_valve_comfort_boost !== undefined &&
                        <p>Boost: {data.boiler_valve_comfort_boost ? "ON" : "OFF"}</p>}
                    {data.therm_setpoint_mode !== undefined && <p>Mode: {data.therm_setpoint_mode}</p>}
                    <p className="relative-time">{time}</p>
                </div>
                <div className="chart-container" style={{flex: 1, minWidth: 0}}>
                    <Line options={options} data={chartData}/>
                </div>
            </div>
        </div>
    );
};

const TemperatureComparisonCard = ({modules}) => {
    // Define a better color palette
    const colors = [
        {border: 'rgb(255, 99, 132)', background: 'rgba(255, 99, 132, 0.1)'},  // Red
        {border: 'rgb(54, 162, 235)', background: 'rgba(54, 162, 235, 0.1)'},  // Blue
        {border: 'rgb(75, 192, 192)', background: 'rgba(75, 192, 192, 0.1)'},  // Teal
        {border: 'rgb(255, 159, 64)', background: 'rgba(255, 159, 64, 0.1)'},  // Orange
        {border: 'rgb(153, 102, 255)', background: 'rgba(153, 102, 255, 0.1)'}, // Purple
        {border: 'rgb(201, 203, 207)', background: 'rgba(201, 203, 207, 0.1)'}  // Gray
    ];

    const chartData = {
        datasets: modules.map((module, index) => {
            if (!module?.measures?.length) {
                return null;
            }

            const colorIndex = index % colors.length;
            const data = module.measures
                .filter(m => m.temperature !== undefined)
                .map(m => ({
                    x: new Date(m.timestamp * 1000),
                    y: m.temperature
                }));

            if (data.length === 0) {
                return null;
            }

            return {
                label: module.name || module.id,
                data: data,
                borderColor: colors[colorIndex].border,
                backgroundColor: colors[colorIndex].background,
                yAxisID: 'temperature',
                pointRadius: 0,
                borderWidth: 2
            };
        }).filter(dataset => dataset !== null)
    };

    const options = {
        responsive: true,
        maintainAspectRatio: false,
        animation: {
            duration: 0 // Disable animations for better performance
        },
        plugins: {
            legend: {
                position: 'top',
            },
            title: {
                display: true,
                text: 'Temperature Comparison'
            }
        },
        scales: {
            x: {
                type: 'time',
                time: {
                    unit: 'hour',
                    displayFormats: {
                        hour: 'HH:mm'
                    }
                },
                title: {
                    display: false
                }
            },
            temperature: {
                type: 'linear',
                display: true,
                position: 'left',
                title: {
                    display: true,
                    text: 'Temperature (°C)'
                },
                grid: {
                    drawOnChartArea: false
                }
            }
        }
    };

    return (
        <div className="card">
            <div className="chart-container" style={{height: '100%', position: 'relative'}}>
                <Line options={options} data={chartData}/>
            </div>
        </div>
    );
};

// Helper function to generate random colors for the lines
function getRandomColor() {
    const letters = '0123456789ABCDEF';
    let color = '#';
    for (let i = 0; i < 6; i++) {
        color += letters[Math.floor(Math.random() * 16)];
    }
    return color;
}

const Message = ({message, severity, timestamp, source}) => {
    const getSeverityColor = () => {
        switch (severity) {
            case 'warning':
                return 'orange';
            case 'error':
                return 'red';
            case 'info':
            default:
                return 'blue';
        }
    };

    const getSourceCode = (source) => {
      switch (source) {
          case 'frontend':
              return 'F';
          case 'server':
              return 'S';
          default:
              return source;
      }
    };

    return (
        <div style={{
            color: getSeverityColor(),
            margin: '0.25em 0',
            fontSize: '0.9em',
            display: 'flex',
            gap: '0.5em',
            lineHeight: '1.5em',
            minHeight: '1.5em'
        }}>
            <span style={{color: 'gray', fontSize: '0.8em'}}>
                {getRelativeTime(new Date(timestamp))}
            </span>
            <span>[{getSourceCode(source)}] {message}</span>
        </div>
    );
};

const units = {
    year: 24 * 60 * 60 * 1000 * 365,
    month: 24 * 60 * 60 * 1000 * 365 / 12,
    day: 24 * 60 * 60 * 1000,
    hour: 60 * 60 * 1000,
    minute: 60 * 1000,
    second: 1000
};

const rtf = new Intl.RelativeTimeFormat('en', {style: 'narrow', numeric: 'auto'});

const getRelativeTime = (d1, d2 = new Date()) => {
    const elapsed = d1 - d2;
    if (isNaN(elapsed)) {
        return "N/A";
    }
    // "Math.abs" accounts for both "past" & "future" scenarios
    for (const u in units) {
        if (Math.abs(elapsed) > units[u] || u === 'second') {
            return rtf.format(Math.round(elapsed / units[u]), u);
        }
    }
};

function App() {
    const [currentPage, setCurrentPage] = useState('dashboard'); // 'dashboard', 'chat', 'eth', or 'recommendations'
    const [homeStatus, setHomeStatus] = useState({})
    const [homesData, setHomesData] = useState({})
    const [logMessages, setMessages] = useState([]);
    const [outdoorModule, setOutdoorModule] = useState({});
    const [poolHouseModule, setPoolHouseModule] = useState({});
    const [homeOfficeModule, setHomeOfficeModule] = useState({});
    const [bedroomModule, setBedroomModule] = useState({});
    const [rainModule, setRainModule] = useState({});
    const [mainStation, setMainStation] = useState({});
    const [therm, setTherm] = useState({});
    const [time, setTime] = useState(new Date());
    const MAX_MESSAGES = 10;

    const addMessage = useCallback((message, severity = 'info', source = 'frontend') => {
        const timestamp = new Date().toISOString();
        setMessages(prev => {
            const newMessages = [...prev, {message, severity, timestamp, source}];
            return newMessages.slice(-MAX_MESSAGES);
        });
    }, []);

    const fetchServerMessages = useCallback(async () => {
        try {
            const response = await fetch("/api/messages", {
                method: "GET",
                headers: {
                    "Authorization": "Bearer " + sessionStorage.getItem("token")
                }
            });
            if (response.status === 200) {
                const serverMessages = await response.json();
                setMessages(prev => {
                    // Filter out previous server logMessages
                    const nonServerMessages = prev.filter(msg => msg.source !== 'server');
                    // Add new server logMessages
                    const newServerMessages = serverMessages.map(msg => ({
                        message: msg.message,
                        severity: msg.severity,
                        timestamp: msg.timestamp,
                        source: 'server'
                    }));
                    // Combine and sort by timestamp
                    const allMessages = [...nonServerMessages, ...newServerMessages]
                        .sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
                    // Keep only the last MAX_MESSAGES
                    return allMessages.slice(-MAX_MESSAGES);
                });
            } else {
                console.error("Failed to fetch server logMessages:", response.status);
            }
        } catch (error) {
            console.error("Error fetching server logMessages:", error);
        }
    }, []);

    async function signup(tokenId) {
        const user = {
            username: tokenId,
            password: "",
        };
        let response = await fetch("/api/auth/signup", {
            method: "POST",
            headers: {
                "Content-Type": "Application/JSON",
            },
            body: JSON.stringify(user),
        });
        if (response.status !== 200) {
            console.dir(response);
            addMessage("Invalid status code at signup: " + response.status + " " + response.statusText + " " + response.statusMessage, 'error');
            return;
        }
        console.log("signup OK");
        let userReturned = await response.json();
        if (userReturned) {
            localStorage.setItem("tokenId", tokenId);
            window.location.replace("/api/auth/authorizeAtmo?id=" + tokenId);
        }
        return userReturned;
    }

    async function getMeasures(module, types) {
        if (!module || !module.id) {
            addMessage("Module not available", 'warning');
            return;
        }
        //console.log(`Fetching measures for module ${module.id} (${module.type}) with types:`, types);
        const params = new URLSearchParams()
        params.append('device_id', module.bridge || module.id);
        params.append('module_id', module.id);
        params.append('scale', '30min');
        params.append('type', types);
        let response = await fetch("/api/getmeasure?" + params, {
            method: "GET",
            headers: {
                "Authorization": "Bearer " + sessionStorage.getItem("token")
            }
        });

        if (response.status === 403) {
            addMessage(`Netatmo authorization expired. Re-authorizing...`, 'warning');
            const tokenId = localStorage.getItem("tokenId");
            if (tokenId) {
                window.location.replace("/api/auth/authorizeAtmo?id=" + tokenId);
            } else {
                addMessage(`No tokenId found. Please sign up again.`, 'error');
            }
            return;
        }

        if (response.status !== 200) {
            addMessage(`getMeasure: ${response.status} ${response.statusText}`, 'error');
            console.log("getMeasure status code: " + response.status);
            return;
        }

        const data = await response.json();
        //console.log(`Received measures for module ${module.id}:`, data);

        if (!data || !data.body || !Array.isArray(data.body)) {
            addMessage(`Invalid response format for module ${module.id}`, 'error');
            return;
        }

        // The response body is an array of measurements
        module.measures = data.body.flatMap(measurement => {
            const begTime = measurement.beg_time;
            const stepTime = measurement.step_time;
            const values = measurement.value;

            // Create an array of measurements, one for each value
            return values.map((value, index) => {
                const timestamp = begTime + (index * stepTime);
                const measurements = {};

                // Map the values based on the requested types
                types.forEach((type, typeIndex) => {
                    const measurementValue = value[typeIndex];
                    switch (type) {
                        case 'temperature':
                            measurements.temperature = measurementValue;
                            break;
                        case 'humidity':
                            measurements.humidity = measurementValue;
                            break;
                        case 'co2':
                            measurements.co2 = measurementValue;
                            break;
                        case 'noise':
                            measurements.noise = measurementValue;
                            break;
                        case 'rain':
                            measurements.rain = measurementValue;
                            break;
                        case 'sum_rain':
                            measurements.sum_rain = measurementValue;
                            break;
                        case 'sum_rain_24':
                            measurements.sum_rain_24 = measurementValue;
                            break;
                        case 'sum_boiler_on':
                            measurements.sum_boiler_on = measurementValue;
                            break;
                        case 'sp_temperature':
                            measurements.sp_temperature = measurementValue;
                            break;
                    }
                });

                return {
                    timestamp: timestamp,
                    ...measurements
                };
            });
        });

        //console.log(`Processed ${module.measures.length} measures for module ${module.id}`);
    }

    async function updateStatus(homeId) {
        if (!homeId) {
            return;
        }
        const startTime = performance.now();

        let response = await fetch("/api/homestatus?home_id=" + homeId, {
            method: "GET",
            headers: {
                "Authorization": "Bearer " + sessionStorage.getItem("token")
            }
        });

        if (response.status === 403) {
            addMessage(`Netatmo authorization expired. Re-authorizing...`, 'warning');
            const tokenId = localStorage.getItem("tokenId");
            if (tokenId) {
                window.location.replace("/api/auth/authorizeAtmo?id=" + tokenId);
            } else {
                addMessage(`No tokenId found. Please sign up again.`, 'error');
            }
            return;
        }

        if (response.status !== 200) {
            addMessage(`homestatus: ${response.status} ${response.statusText}`, 'error');
            return;
        }
        let homeStatus = await response.json();

        if (!homeStatus.body || !homeStatus.body.home || !homeStatus.body.home.modules) {
            addMessage("Invalid home status structure received", 'error');
            return;
        }

        if (homeStatus.body.home.modules.length < 7) {
            addMessage(`Unexpected module count: ${homeStatus.body.home.modules.length} modules found`, 'warning');
        }

        setHomeStatus(homeStatus.body.home);

        // Create a map to track module updates
        const moduleUpdates = new Map();

        // First, collect all the promises for module updates
        const updatePromises = homeStatus.body.home.modules.map(async module => {
            try {
                if (module.type === 'NAMain') {
                    await getMeasures(module, ['temperature', 'humidity', 'co2', 'noise']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('mainStation', module);
                    } else {
                        addMessage(`No measures received for main station ${module.id}`, 'warning');
                    }
                } else if (module.type === 'NATherm1') {
                    await getMeasures(module, ['temperature', 'sum_boiler_on', 'sp_temperature']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('therm', module);
                    } else {
                        addMessage(`No measures received for therm ${module.id}`, 'warning');
                    }
                } else if (module.type === 'NAModule3') {
                    await getMeasures(module, ['rain', 'sum_rain']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('rainModule', module);
                    } else {
                        addMessage(`No measures received for rain module ${module.id}`, 'warning');
                    }
                }
                if (module.id === '02:00:00:a9:a2:14') {
                    await getMeasures(module, ['temperature', 'humidity']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('outdoorModule', module);
                    } else {
                        addMessage(`No measures received for outdoor module ${module.id}`, 'warning');
                    }
                } else if (module.id === '03:00:00:0e:f9:6c') {
                    await getMeasures(module, ['temperature', 'humidity', 'co2']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('poolHouseModule', module);
                    } else {
                        addMessage(`No measures received for pool house module ${module.id}`, 'warning');
                    }
                } else if (module.id === '03:00:00:0e:f9:3a') {
                    await getMeasures(module, ['temperature', 'humidity', 'co2']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('homeOfficeModule', module);
                    } else {
                        addMessage(`No measures received for home office module ${module.id}`, 'warning');
                    }
                } else if (module.id === '03:00:00:0e:eb:16') {
                    await getMeasures(module, ['temperature', 'humidity', 'co2']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('bedroomModule', module);
                    } else {
                        addMessage(`No measures received for bedroom module ${module.id}`, 'warning');
                    }
                }
            } catch (error) {
                addMessage(`Error updating module ${module.id}: ${error.message}`, 'error');
            }
        });

        // Wait for all module updates to complete
        await Promise.all(updatePromises);

        // Update state only after all modules are processed
        if (moduleUpdates.has('outdoorModule')) setOutdoorModule(moduleUpdates.get('outdoorModule'));
        if (moduleUpdates.has('poolHouseModule')) setPoolHouseModule(moduleUpdates.get('poolHouseModule'));
        if (moduleUpdates.has('homeOfficeModule')) setHomeOfficeModule(moduleUpdates.get('homeOfficeModule'));
        if (moduleUpdates.has('bedroomModule')) setBedroomModule(moduleUpdates.get('bedroomModule'));
        if (moduleUpdates.has('rainModule')) setRainModule(moduleUpdates.get('rainModule'));
        if (moduleUpdates.has('mainStation')) setMainStation(moduleUpdates.get('mainStation'));
        if (moduleUpdates.has('therm')) setTherm(moduleUpdates.get('therm'));

        const endTime = performance.now();
        const duration = (endTime - startTime) / 1000;
        if (duration > 2) {
            addMessage(`Slow update in ${duration.toFixed(2)}s with ${moduleUpdates.size} modules`, 'info');
        }
    }

    useEffect(() => {
            const statusInterval = setInterval(async () => {
                setTime(new Date());
                if (!homesData) {
                    addMessage("Waiting for homes data...", 'info');
                    return;
                }
                await updateStatus(homesData.id)
            }, 60000);
    
            const messageInterval = setInterval(() => {
                fetchServerMessages();
            }, 30000); // Fetch server logMessages every 30 seconds
    
            return () => {
                clearInterval(statusInterval);
                clearInterval(messageInterval);
            };
        }, [homesData, fetchServerMessages]);

    async function handleToken(tokenId) {
        const authRequest = {
            username: tokenId,
        };
        let response = await fetch("/api/auth/login", {
            method: "POST",
            headers: {
                "Content-Type": "Application/JSON",
            },
            body: JSON.stringify(authRequest),
        });
        let responseJson;
        if (response.status === 200) {
            console.log("logged in");
            responseJson = await response.json();
        } else if (response.status === 404) {
            console.log("Token not found on server!")
            responseJson = await signup(tokenId);
        } else {
            console.log("Unexpected response status for /auth/login");
            console.dir(response);
            return null;
        }

        if (!responseJson || !responseJson.token) {
            console.log("No token received from server");
            return null;
        }

        // Set the token in sessionStorage
        sessionStorage.setItem("token", responseJson.token);
        return responseJson.token;
    }

    // Add JWT token refresh every 6 hours
    useEffect(() => {
        const refreshToken = async () => {
            const tokenId = localStorage.getItem("tokenId");
            if (tokenId) {
                try {
                    const token = await handleToken(tokenId);
                    if (token) {
                        console.log("JWT token refreshed successfully");
                    }
                } catch (error) {
                    console.error("Error refreshing JWT token:", error);
                }
            }
        };

        // Set up interval for every 6 hours
        const tokenRefreshInterval = setInterval(refreshToken, 6 * 60 * 60 * 1000);

        return () => clearInterval(tokenRefreshInterval);
    }, []);

    useEffect(async () => {
            let tokenId = localStorage.getItem("tokenId");
            if (tokenId) {
                console.log("I have a tokenId");
                const token = await handleToken(tokenId);
                if (token) {
                    try {
                        const whoamiResponse = await fetch("/api/whoami", {
                            method: "GET",
                            headers: {
                                "Authorization": "Bearer " + token
                            }
                        });
                        const whoamiData = await whoamiResponse.json();
                        console.dir(whoamiData);

                        const homesDataResponse = await fetch("/api/homesdata", {
                            method: "GET",
                            headers: {
                                "Authorization": "Bearer " + token
                            }
                        });

                        if (homesDataResponse.status === 401 || homesDataResponse.status === 403) {
                            addMessage(`Netatmo authorization expired. Re-authorizing...`, 'warning');
                            const tokenId = localStorage.getItem("tokenId");
                            if (tokenId) {
                                window.location.replace("/api/auth/authorizeAtmo?id=" + tokenId);
                            } else {
                                addMessage(`No tokenId found. Please sign up again.`, 'error');
                            }
                            return;
                        }

                        if (homesDataResponse.status !== 200) {
                            try {
                                const errorData = await homesDataResponse.json();
                                addMessage(`Netatmo API Error (${errorData.error?.code || 'unknown'}): ${errorData.error?.message || 'Unknown error'}`, 'error');
                            } catch (e) {
                                addMessage(`Netatmo API Error: ${homesDataResponse.status} ${homesDataResponse.statusText}`, 'error');
                            }
                            return;
                        }

                        const homesData = await homesDataResponse.json();

                        console.dir(homesData.body.homes[0]);
                        setHomesData(homesData.body.homes[0]);
                        let homeId = homesData.body.homes[0].id;
                        await updateStatus(homeId);
                    } catch (error) {
                        console.error("Error fetching data:", error);
                        addMessage("Error fetching data: " + error.message, 'error');
                    }
                }
            }
            if (!tokenId) {
                console.log("No token ID yet !")
                tokenId = window.crypto.randomUUID();
                console.log("Token ID created: " + tokenId);
                await signup(tokenId);
            }
        }, []
    );

    return (
            <div className="App">
                <div style={{
                    display: 'flex',
                    gap: '1rem',
                    padding: '1rem',
                    justifyContent: 'center',
                    background: '#f0f0f0',
                    borderBottom: '2px solid #ddd'
                }}>
                    <button
                        onClick={() => setCurrentPage('dashboard')}
                        style={{
                            padding: '0.75rem 1.5rem',
                            background: currentPage === 'dashboard' ? '#667eea' : 'white',
                            color: currentPage === 'dashboard' ? 'white' : '#333',
                            border: '1px solid #667eea',
                            borderRadius: '8px',
                            cursor: 'pointer',
                            fontWeight: '600',
                            transition: 'all 0.2s'
                        }}
                    >
                        📊 Dashboard
                    </button>
                    <button
                        onClick={() => setCurrentPage('chat')}
                        style={{
                            padding: '0.75rem 1.5rem',
                            background: currentPage === 'chat' ? '#667eea' : 'white',
                            color: currentPage === 'chat' ? 'white' : '#333',
                            border: '1px solid #667eea',
                            borderRadius: '8px',
                            cursor: 'pointer',
                            fontWeight: '600',
                            transition: 'all 0.2s'
                        }}
                    >
                        🤖 AI Chat
                    </button>
                    <button
                        onClick={() => setCurrentPage('eth')}
                        style={{
                            padding: '0.75rem 1.5rem',
                            background: currentPage === 'eth' ? '#667eea' : 'white',
                            color: currentPage === 'eth' ? 'white' : '#333',
                            border: '1px solid #667eea',
                            borderRadius: '8px',
                            cursor: 'pointer',
                            fontWeight: '600',
                            transition: 'all 0.2s'
                        }}
                    >
                        📈 ETH Trading
                    </button>
                    <button
                        onClick={() => setCurrentPage('recommendations')}
                        style={{
                            padding: '0.75rem 1.5rem',
                            background: currentPage === 'recommendations' ? '#f59e0b' : 'white',
                            color: currentPage === 'recommendations' ? 'white' : '#333',
                            border: '1px solid #f59e0b',
                            borderRadius: '8px',
                            cursor: 'pointer',
                            fontWeight: '600',
                            transition: 'all 0.2s'
                        }}
                    >
                        🧠 AI Memory
                    </button>
                </div>
                
                {currentPage === 'chat' ? (
                    <Chat />
                ) : currentPage === 'eth' ? (
                    <EthTrading />
                ) : currentPage === 'recommendations' ? (
                    <RecommendationHistory />
                ) : (
                <div className={"grid"}>
                    <TemperatureComparisonCard
                        modules={[
                            {...outdoorModule, name: 'Outdoor'},
                            {...mainStation, name: 'Dining Room'},
                            {...poolHouseModule, name: 'Pool House'},
                        {...homeOfficeModule, name: 'Home Office'},
                        {...bedroomModule, name: 'Bedroom'},
                        {...therm, name: 'Living Room'},
                    ]}
                />
                <MeasurementCard
                    title="Outdoor"
                    data={{
                        ...outdoorModule,
                        meta: {
                            rf_status: outdoorModule.rf_status,
                            wifi_status: outdoorModule.wifi_status,
                            battery_percent: outdoorModule.battery_percent,
                            battery_vp: outdoorModule.battery_vp
                        }
                    }}
                    measures={outdoorModule.measures}
                    time={getRelativeTime(new Date(outdoorModule.ts * 1000))}
                />
                <MeasurementCard
                    title="Rain"
                    data={{
                        ...rainModule,
                        meta: {
                            rf_status: rainModule.rf_status,
                            wifi_status: rainModule.wifi_status,
                            battery_percent: rainModule.battery_percent,
                            battery_vp: rainModule.battery_vp
                        }
                    }}
                    measures={rainModule.measures}
                    time={getRelativeTime(new Date(rainModule.ts * 1000))}
                />
                <MeasurementCard
                    title="Living Room"
                    data={{
                        ...mainStation,
                        meta: {
                            rf_status: mainStation.rf_status,
                            wifi_status: mainStation.wifi_status,
                            battery_percent: mainStation.battery_percent,
                            battery_vp: mainStation.battery_vp
                        }
                    }}
                    measures={mainStation.measures}
                    time={getRelativeTime(new Date(mainStation.ts * 1000))}
                />
                <MeasurementCard
                    title="Pellets"
                    data={{
                        ...therm,
                        therm_measured_temperature: homeStatus.rooms ? homeStatus.rooms[0].therm_measured_temperature : undefined,
                        therm_setpoint_temperature: homeStatus.rooms ? homeStatus.rooms[0].therm_setpoint_temperature : undefined,
                        boiler_status: therm.boiler_status,
                        boiler_valve_comfort_boost: therm.boiler_valve_comfort_boost,
                        therm_setpoint_mode: homeStatus.rooms ? homeStatus.rooms[0].therm_setpoint_mode : undefined,
                        meta: {
                            rf_status: therm.rf_status,
                            wifi_status: therm.wifi_status,
                            battery_percent: therm.battery_percent,
                            battery_vp: therm.battery_vp
                        }
                    }}
                    measures={therm.measures}
                    time={getRelativeTime(new Date(therm.ts * 1000))}
                />
                <MeasurementCard
                    title="Pool House"
                    data={{
                        ...poolHouseModule,
                        meta: {
                            rf_status: poolHouseModule.rf_status,
                            wifi_status: poolHouseModule.wifi_status,
                            battery_percent: poolHouseModule.battery_percent,
                            battery_vp: poolHouseModule.battery_vp
                        }
                    }}
                    measures={poolHouseModule.measures}
                    time={getRelativeTime(new Date(poolHouseModule.ts * 1000))}
                />
                <MeasurementCard
                    title="Home Office"
                    data={{
                        ...homeOfficeModule,
                        meta: {
                            rf_status: homeOfficeModule.rf_status,
                            wifi_status: homeOfficeModule.wifi_status,
                            battery_percent: homeOfficeModule.battery_percent,
                            battery_vp: homeOfficeModule.battery_vp
                        }
                    }}
                    measures={homeOfficeModule.measures}
                    time={getRelativeTime(new Date(homeOfficeModule.ts * 1000))}
                />
                <MeasurementCard
                    title="Bedroom"
                    data={{
                        ...bedroomModule,
                        meta: {
                            rf_status: bedroomModule.rf_status,
                            wifi_status: bedroomModule.wifi_status,
                            battery_percent: bedroomModule.battery_percent,
                            battery_vp: bedroomModule.battery_vp
                        }
                    }}
                    measures={bedroomModule.measures}
                    time={getRelativeTime(new Date(bedroomModule.ts * 1000))}
                />
                <div className={"card"}>
                    <p>Last update: {time.toISOString()}</p>
                    <div style={{
                        height: `${MAX_MESSAGES * 1.5}em`,
                        overflowY: 'auto',
                        padding: '0.25em'
                    }}>
                        <div className="logMessages-container">
                            {logMessages.map((msg, index) => (
                                <Message
                                    key={index}
                                    message={msg.message}
                                    severity={msg.severity}
                                    timestamp={msg.timestamp}
                                    source={msg.source}
                                />
                            ))}
                        </div>
                    </div>
                </div>

            </div>
                )}
        </div>
    );

}

export default App;

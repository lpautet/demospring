import logo from './logo.svg';
import './App.css';
import {useEffect, useState} from "react";
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
    TimeScale
} from 'chart.js';
import { Line, Bar } from 'react-chartjs-2';
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
    TimeScale
);

const MeasurementCard = ({ title, data, measures, time }) => {
    //console.log("MeasurementCard", title, data, measures, time);
    if (!measures) {
        return;
    }
    //console.dir(data)
    //console.log(data.measures);
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
            <p>{title}</p>
            <div style={{ display: 'flex', flexDirection: 'row', flex: 1, minHeight: 0 }}>
                <div className="measurements" style={{ flex: '0 0 auto', paddingRight: '1em' }}>
                    {data.temperature !== undefined && <p className="temperature">{data.temperature}&deg;</p>}
                    {data.humidity !== undefined && <p>{data.humidity}%</p>}
                    {data.co2 !== undefined && <p>CO2 {data.co2}ppm</p>}
                    {data.noise !== undefined && <p>noise {data.noise}dB</p>}
                    {data.rain !== undefined && <p>{data.rain}mm/h</p>}
                    {data.sum_rain !== undefined && <p>last 1h: {data.sum_rain}mm</p>}
                    {data.sum_rain_24 !== undefined && <p>last 24h: {data.sum_rain_24}mm</p>}
                    {data.therm_measured_temperature !== undefined && <p>Temp: {data.therm_measured_temperature}&deg;</p>}
                    {data.therm_setpoint_temperature !== undefined && <p>Set: {data.therm_setpoint_temperature}&deg;</p>}
                    {data.boiler_status !== undefined && <p>Heating: {data.boiler_status ? "ON" : "OFF"}</p>}
                    {data.boiler_valve_comfort_boost !== undefined && <p>Boost: {data.boiler_valve_comfort_boost ? "ON" : "OFF"}</p>}
                    {data.therm_setpoint_mode !== undefined && <p>Mode: {data.therm_setpoint_mode}</p>}
                </div>
                <div className="chart-container" style={{ flex: 1, minWidth: 0 }}>
                    <Line options={options} data={chartData} />
                </div>
            </div>
            <p className="relative-time">{time}</p>
        </div>
    );
};

const TemperatureComparisonCard = ({ modules }) => {
    const chartData = {
        labels: [],
        datasets: []
    };

    // Find the module with the most measurements to use as reference for timestamps
    const referenceModule = modules.reduce((prev, current) => {
        return (prev?.measures?.length || 0) > (current?.measures?.length || 0) ? prev : current;
    }, null);

    if (referenceModule?.measures) {
        chartData.labels = referenceModule.measures.map(m => new Date(m.timestamp * 1000));
    }

    // Define a better color palette
    const colors = [
        { border: 'rgb(255, 99, 132)', background: 'rgba(255, 99, 132, 0.1)' },  // Red
        { border: 'rgb(54, 162, 235)', background: 'rgba(54, 162, 235, 0.1)' },  // Blue
        { border: 'rgb(75, 192, 192)', background: 'rgba(75, 192, 192, 0.1)' },  // Teal
        { border: 'rgb(255, 159, 64)', background: 'rgba(255, 159, 64, 0.1)' },  // Orange
        { border: 'rgb(153, 102, 255)', background: 'rgba(153, 102, 255, 0.1)' }, // Purple
        { border: 'rgb(201, 203, 207)', background: 'rgba(201, 203, 207, 0.1)' }  // Gray
    ];

    // Add temperature datasets for each module that has temperature measurements
    modules.forEach((module, index) => {
        if (module?.measures?.some(m => m.temperature !== undefined)) {
            const colorIndex = index % colors.length;
            chartData.datasets.push({
                label: module.name || module.id,
                data: referenceModule.measures.map(refTime => {
                    const matchingMeasure = module.measures.find(m => m.timestamp === refTime.timestamp);
                    return matchingMeasure?.temperature;
                }),
                borderColor: colors[colorIndex].border,
                backgroundColor: colors[colorIndex].background,
                yAxisID: 'temperature',
                pointRadius: 0,
                borderWidth: 2
            });
        }
    });

    const options = {
        responsive: true,
        maintainAspectRatio: false,
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
            <div className="chart-container" style={{ height: '100%', position: 'relative' }}>
                <Line options={options} data={chartData} />
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

function App() {

    // in miliseconds
    const units = {
        year: 24 * 60 * 60 * 1000 * 365,
        month: 24 * 60 * 60 * 1000 * 365 / 12,
        day: 24 * 60 * 60 * 1000,
        hour: 60 * 60 * 1000,
        minute: 60 * 1000,
        second: 1000
    }

    const rtf = new Intl.RelativeTimeFormat('en', {style: 'narrow', numeric: 'auto'})

    const getRelativeTime = (d1, d2 = new Date()) => {
        const elapsed = d1 - d2
        if (isNaN(elapsed)) {
            return "N/A"
        }
        // "Math.abs" accounts for both "past" & "future" scenarios
        for (const u in units)
            if (Math.abs(elapsed) > units[u] || u === 'second')
                return rtf.format(Math.round(elapsed / units[u]), u)
    }

    const [homeStatus, setHomeStatus] = useState({})
    const [homesData, setHomesData] = useState({})
    const [msg, setMsg] = useState("");
    const [outdoorModule, setOutdoorModule] = useState({});
    const [poolHouseModule, setPoolHouseModule] = useState({});
    const [homeOfficeModule, setHomeOfficeModule] = useState({});
    const [bedroomModule, setBedroomModule] = useState({});
    const [rainModule, setRainModule] = useState({});
    const [mainStation, setMainStation] = useState({});
    const [therm, setTherm] = useState({});
    const [time, setTime] = useState(new Date());

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
            setMsg("Invalid status code at signup: " + response.status + " " + response.statusText + " " + response.statusMessage);
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
            console.log("Module not available:", module);
            return;
        }
        //console.log(`Fetching measures for module ${module.id} (${module.type}) with types:`, types);
        const params = new URLSearchParams()
        params.append('device_id', module.bridge || module.id);
        params.append('module_id', module.id);
        params.append('scale', '30min');
        params.append('type',types);
        let response = await fetch("/api/getmeasure?" + params, {
            method: "GET",
            headers: {
                "Authorization": "Bearer " + sessionStorage.getItem("token")
            }
        });
        
        if (response.status === 403) {
            const errorData = await response.json();
            setMsg(`Netatmo API Error (${errorData.code}): ${errorData.message}`);
            return;
        }
        
        if (response.status !== 200) {
            setMsg("getMeasure: " + response.status + " " + response.statusText);
            console.log("getMeasure status code: " + response.status);
            return;
        }
        
        const data = await response.json();
        //console.log(`Received measures for module ${module.id}:`, data);
        
        if (!data || !data.body || !Array.isArray(data.body)) {
            console.error("Invalid response format for module", module.id, ":", data);
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
                    switch(type) {
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
        //console.log("updateStatus {}", homeId);
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

        if (response.status !== 200) {
            setMsg("homestatus: " + response.status + " " + response.statusText);
            console.log("homestatus status code: " + response.status);
            return;
        }
        let homeStatus = await response.json();
        //console.log("Received home status:", homeStatus);
        
        if (!homeStatus.body || !homeStatus.body.home || !homeStatus.body.home.modules) {
            console.error("Invalid home status structure:", homeStatus);
            return;
        }
        
        if (homeStatus.body.home.modules.length < 7) {
            console.warn("Unexpected module count, available modules:", homeStatus.body.home.modules.map(m => `${m.id} (${m.type})`).join(', '));
        }
        setHomeStatus(homeStatus.body.home);
        
        // Create a map to track module updates
        const moduleUpdates = new Map();
        
        // First, collect all the promises for module updates
        const updatePromises = homeStatus.body.home.modules.map(async module => {
            try {
                //console.log(`Processing module ${module.id} (${module.type})`);
                if (module.type === 'NAMain') {
                    await getMeasures(module, ['temperature', 'humidity', 'co2', 'noise']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('mainStation', module);
                    } else {
                        console.warn(`No measures received for main station ${module.id}`);
                    }
                } else if (module.type === 'NATherm1') {
                    await getMeasures(module, ['temperature', 'sum_boiler_on', 'sp_temperature']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('therm', module);
                    } else {
                        console.warn(`No measures received for therm ${module.id}`);
                    }
                } else if (module.type === 'NAModule3') {
                    await getMeasures(module, ['rain', 'sum_rain']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('rainModule', module);
                    } else {
                        console.warn(`No measures received for rain module ${module.id}`);
                    }
                }
                if (module.id === '02:00:00:a9:a2:14') {                
                    await getMeasures(module, ['temperature', 'humidity']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('outdoorModule', module);
                    } else {
                        console.warn(`No measures received for outdoor module ${module.id}`);
                    }
                } else if (module.id === '03:00:00:0e:f9:6c') {
                    await getMeasures(module, ['temperature', 'humidity', 'co2']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('poolHouseModule', module);
                    } else {
                        console.warn(`No measures received for pool house module ${module.id}`);
                    }
                } else if (module.id === '03:00:00:0e:f9:3a') {
                    await getMeasures(module, ['temperature', 'humidity', 'co2']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('homeOfficeModule', module);
                    } else {
                        console.warn(`No measures received for home office module ${module.id}`);
                    }
                } else if (module.id === '03:00:00:0e:eb:16') {
                    await getMeasures(module, ['temperature', 'humidity', 'co2']);
                    if (module.measures && module.measures.length > 0) {
                        moduleUpdates.set('bedroomModule', module);
                    } else {
                        console.warn(`No measures received for bedroom module ${module.id}`);
                    }
                }
            } catch (error) {
                console.error(`Error updating module ${module.id}:`, error);
            }
        });

        // Wait for all module updates to complete
        await Promise.all(updatePromises);

        //console.log("Module updates completed. Updating state with:", moduleUpdates);

        // Update state only after all modules are processed
        if (moduleUpdates.has('outdoorModule')) setOutdoorModule(moduleUpdates.get('outdoorModule'));
        if (moduleUpdates.has('poolHouseModule')) setPoolHouseModule(moduleUpdates.get('poolHouseModule'));
        if (moduleUpdates.has('homeOfficeModule')) setHomeOfficeModule(moduleUpdates.get('homeOfficeModule'));
        if (moduleUpdates.has('bedroomModule')) setBedroomModule(moduleUpdates.get('bedroomModule'));
        if (moduleUpdates.has('rainModule')) setRainModule(moduleUpdates.get('rainModule'));
        if (moduleUpdates.has('mainStation')) setMainStation(moduleUpdates.get('mainStation'));
        if (moduleUpdates.has('therm')) setTherm(moduleUpdates.get('therm'));

        const endTime = performance.now();
        const duration = (endTime - startTime) / 1000; // Convert to seconds
        console.log(`updateStatus ${homeId} done in ${duration.toFixed(2)}s with ${moduleUpdates.size} modules updated`);
    }
    
    useEffect(() => {
        const interval = setInterval(async () => {
            setTime(new Date());
            if (!homesData) {
                setMsg("Waiting for homes data...");
                return;
            }
            await updateStatus(homesData.id)
        }, 60000);

        return () => clearInterval(interval);
    }, [homesData]);

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
                        const homesData = await homesDataResponse.json();

                        console.dir(homesData.body.homes[0]);
                        setHomesData(homesData.body.homes[0]);
                        setMsg("");
                        let homeId = homesData.body.homes[0].id;
                        await updateStatus(homeId);
                    } catch (error) {
                        console.error("Error fetching data:", error);
                        setMsg("Error fetching data: " + error.message);
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
            <div className={"grid"}>
                <TemperatureComparisonCard 
                    modules={[
                        { ...outdoorModule, name: 'Outdoor' },
                        { ...mainStation, name: 'Dining Room' },
                        { ...poolHouseModule, name: 'Pool House' },
                        { ...homeOfficeModule, name: 'Home Office' },
                        { ...bedroomModule, name: 'Bedroom' },
                        { ...therm, name: 'Living Room' }, 
                    ]}
                />
                <MeasurementCard 
                    title="Outdoor"
                    data={outdoorModule}
                    measures={outdoorModule.measures}
                    time={getRelativeTime(new Date(outdoorModule.ts * 1000))}
                />
                  <MeasurementCard 
                    title="Rain"
                    data={rainModule}
                    measures={rainModule.measures}
                    time={getRelativeTime(new Date(rainModule.ts * 1000))}
                />
                <MeasurementCard 
                    title="Living Room"
                    data={mainStation}
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
                        therm_setpoint_mode: homeStatus.rooms ? homeStatus.rooms[0].therm_setpoint_mode : undefined
                    }}
                    measures={therm.measures}
                    time=""
                />            
                <MeasurementCard 
                    title="Pool House"
                    data={poolHouseModule}
                    measures={poolHouseModule.measures}
                    time={getRelativeTime(new Date(poolHouseModule.ts * 1000))}
                />
                <MeasurementCard 
                    title="Home Office"
                    data={homeOfficeModule}
                    measures={homeOfficeModule.measures}
                    time={getRelativeTime(new Date(homeOfficeModule.ts * 1000))}
                />
            
                <MeasurementCard 
                    title="Bedroom"
                    data={bedroomModule}
                    measures={bedroomModule.measures}
                    time={getRelativeTime(new Date(bedroomModule.ts * 1000))}
                />     
                    <div className={"card"}>
                    <p>Time</p>
                    <p>{time.toISOString()}</p>
                    <p>Message: {msg}</p>
                </div>                     
            
            </div>
        </div>
    );

}

export default App;

import logo from './logo.svg';
import './App.css';
import {useEffect, useState} from "react";

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

    function signup(tokenId) {
        const user = {
            username: tokenId,
            password: "",
        };
        fetch("/signup", {
            method: "POST",
            headers: {
                "Content-Type": "Application/JSON",
            },
            body: JSON.stringify(user),
        }).then(response => {
            if (response.status !== 200) {
                console.dir(response);
                setMsg("Invalid status code at signup: " + response.status + " " + response.statusText + " " + response.statusMessage)
            } else {
                console.log("signup OK");
                return response.json();
            }
        }).then(user => {
            if (user) {
                localStorage.setItem("tokenId", tokenId);
                window.location.replace("/loginAtmo?id=" + tokenId);
            }
        });
    }

    function updateStatus(homeId) {
        if (!homeId) {
            return;
        }
        fetch("/api/homestatus?home_id=" + homeId, {
            method: "GET",
            headers: {
                "Authorization": "Bearer " + sessionStorage.getItem("token")
            }
        })
            .then(response => {
                if (response.status !== 200) {
                    setMsg("homestatus: " + response.status + " " + response.statusText);
                    console.log("homestatus status code: " + response.status);
                }
                return response.json();
            })
            .then(homeStatus => {
                setHomeStatus(homeStatus.body.home);
                setMsg("");
                //console.dir(homeStatus.body);
                homeStatus.body.home.modules.forEach(module => {
                    if (module.type === 'NAMain') {
                        setMainStation(module);
                    } else if (module.type === 'NATherm1') {
                        setTherm(module);
                    } else if (module.type === 'NAModule3') {
                        setRainModule(module);
                    }
                    if (module.id === '02:00:00:a9:a2:14') {
                        setOutdoorModule(module);
                    } else if (module.id === '03:00:00:0e:f9:6c') {
                        setPoolHouseModule(module);
                    } else if (module.id === '03:00:00:0e:f9:3a') {
                        setHomeOfficeModule(module);
                    } else if (module.id === '03:00:00:0e:eb:16') {
                        setBedroomModule(module);
                    }
                })
            })
    }

    useEffect(() => {
        const interval = setInterval(() => {
            setTime(new Date());
            if (!homesData) {
                setMsg("Waiting for homes data...");
                return;
            }
            updateStatus(homesData.id)
        }, 20000);

        return () => clearInterval(interval);
    }, [homesData]);

    useEffect(() => {
            let tokenId = localStorage.getItem("tokenId");
            if (tokenId) {
                console.log("I have a tokenId");
                const authRequest = {
                    username: tokenId,
                };
                fetch("/login", {
                    method: "POST",
                    headers: {
                        "Content-Type": "Application/JSON",
                    },
                    body: JSON.stringify(authRequest),
                }).then(response => {
                    if (response.status === 200) {
                        return response.json();
                    } else if (response.status === 404) {
                        console.log("Token not found on server!")
                        return signup(tokenId);
                    } else {
                        console.dir(response);
                    }
                }).then(responseJson => {
                    sessionStorage.setItem("token", responseJson.token);
                    return fetch("/api/whoami", {
                        method: "GET",
                        headers: {
                            "Authorization": "Bearer " + sessionStorage.getItem("token")
                        }
                    });
                }).then(response => {
                    return response.json();
                }).then(responseJson => {
                    console.dir(responseJson);
                }).then(x => {
                    return fetch("/api/homesdata", {
                        method: "GET",
                        headers: {
                            "Authorization": "Bearer " + sessionStorage.getItem("token")
                        }
                    })
                }).then(response => response.json())
                    .then(homesData => {
                        console.dir(homesData.body.homes[0]);
                        setHomesData(homesData.body.homes[0]);
                        setMsg("");
                        return homesData.body.homes[0].id;
                    }).then(homeId => {
                    return updateStatus(homeId);
                });
            }
            if (!tokenId) {
                console.log("No token ID yet !")
                tokenId = window.crypto.randomUUID();
                console.log("Token ID created: " + tokenId);
                return signup(tokenId);
            }
        },[]
    );

    return (
        <div className="App">
            <div className={"grid"}>
                <div className={"outdoor"}>
                    <p>Outdoor</p>
                    <p className={"temperature"}>{outdoorModule.temperature}&deg;</p>
                    <p>{outdoorModule.humidity}%</p>
                    <p className={"relative-time"}>{getRelativeTime(new Date(outdoorModule.ts * 1000))}</p>
                </div>
                <div className={"card"}>
                    <p>Indoor</p>
                    <p className={"temperature"}>{mainStation.temperature}&deg;</p>
                    <p>{mainStation.humidity}%</p>
                    <p>CO2 {mainStation.co2}ppm</p>
                    <p>noise {mainStation.noise}dB</p>
                    <p className={"relative-time"}>{getRelativeTime(new Date(mainStation.ts * 1000))}</p>

                </div>
                <div className={"card"}>
                    <p>Rain</p>
                    <p>{rainModule.rain}mm/h</p>
                    <p>last 1h: {rainModule.sum_rain_1}mm</p>
                    <p>last 24h: {rainModule.sum_rain_24}mm</p>
                    <p className={"relative-time"}>{getRelativeTime(new Date(rainModule.ts * 1000))}</p>
                </div>
                <div className={"card"}>
                    <p>Pool House</p>
                    <p className={"temperature"}>{poolHouseModule.temperature}&deg;</p>
                    <p>{poolHouseModule.humidity}%</p>
                    <p>CO2 {poolHouseModule.co2}ppm</p>
                    <p className={"relative-time"}>{getRelativeTime(new Date(poolHouseModule.ts * 1000))}</p>
                </div>
                <div className={"card"}>
                    <p>Home Office</p>
                    <p className={"temperature"}>{homeOfficeModule.temperature}&deg;</p>
                    <p>{homeOfficeModule.humidity}%</p>
                    <p>CO2 {homeOfficeModule.co2}ppm</p>
                    <p className={"relative-time"}>{getRelativeTime(new Date(homeOfficeModule.ts * 1000))}</p>
                </div>
                <div className={"card"}>
                    <p>Bedroom</p>
                    <p className={"temperature"}>{bedroomModule.temperature}&deg;</p>
                    <p>{bedroomModule.humidity}%</p>
                    <p>CO2 {bedroomModule.co2}ppm</p>
                    <p className={"relative-time"}>{getRelativeTime(new Date(bedroomModule.ts * 1000))}</p>
                </div>
                <div className={"card"}>
                    <p>Pellets</p>
                    <p>Temp: {homeStatus.rooms ? homeStatus.rooms[0].therm_measured_temperature : ""}&deg;</p>
                    <p>Set: {homeStatus.rooms ? homeStatus.rooms[0].therm_setpoint_temperature: ""}&deg;</p>
                    <p>Heating: {therm.boiler_status ? "ON" : "OFF"}</p>
                    <p>Boost: {therm.boiler_valve_comfort_boost ? "ON" : "OFF"}</p>
                    <p>Mode: {homeStatus.rooms ? homeStatus.rooms[0].therm_setpoint_mode : ""}</p>
                </div>
                <div className={"card"}>
                    <p>Time</p>
                    <p>{time.toISOString()}</p>
                </div>
                <div className={"card"}>
                    <p>Message: {msg}</p>
                </div>
            </div>
        </div>
    );

}

export default App;

import logo from './logo.svg';
import './App.css';
import {useEffect, useState} from "react";

function App() {

    // in miliseconds
    const units = {
        year  : 24 * 60 * 60 * 1000 * 365,
        month : 24 * 60 * 60 * 1000 * 365/12,
        day   : 24 * 60 * 60 * 1000,
        hour  : 60 * 60 * 1000,
        minute: 60 * 1000,
        second: 1000
    }

    const rtf = new Intl.RelativeTimeFormat('en', { style: 'narrow', numeric: 'auto' })

    const getRelativeTime = (d1, d2 = new Date()) => {
        const elapsed = d1 - d2
        if (isNaN(elapsed)) {
            return "N/A"
        }
        // "Math.abs" accounts for both "past" & "future" scenarios
        for (const u in units)
            if (Math.abs(elapsed) > units[u] || u === 'second')
                return rtf.format(Math.round(elapsed/units[u]), u)
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
                console.dir(user);
                localStorage.setItem("tokenId", tokenId);
                window.location.replace("/loginAtmo?id=" + tokenId);
            }
        });
    }

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
                console.dir(responseJson);
                sessionStorage.setItem("token", responseJson.token);
                return fetch("/api/whoami", {
                    method: "GET",
                    headers: {
                        "Authorization": "Bearer " + sessionStorage.getItem("token")
                    }
                });
            }).then(response => {
                console.dir(response);
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
                    console.dir(homesData);
                    console.dir(homesData.body.homes[0]);
                    setHomesData(homesData.body.homes[0]);
                    return homesData.body.homes[0].id;
                }).then(homeId => {
                return fetch("/api/homestatus?home_id="+homeId, {
                    method: "GET",
                    headers: {
                        "Authorization": "Bearer " + sessionStorage.getItem("token")
                    }
                })
            }).then(response => response.json())
                .then(homeStatus => {
                    console.dir(homeStatus.body.home);
                    setHomeStatus(homeStatus.body.home);
                    homeStatus.body.home.modules.forEach(module => {
                        console.log("Module " + module.id);
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
        if (!tokenId) {
            console.log("No token ID yet !")
            tokenId = window.crypto.randomUUID();
            console.log("Token ID created: " + tokenId);
            return signup(tokenId);
        }
    }, []);

    return (
        <div className="App">
            <header className="App-header">
                <p>Outdoor temperature
                    is {outdoorModule.temperature} ({getRelativeTime(new Date(outdoorModule.ts * 1000))}) and humidity
                    is {outdoorModule.humidity}%</p>
                <p>Rain {rainModule.rain}mm/h ({getRelativeTime(new Date(rainModule.ts * 1000))}), last 1h: {rainModule.sum_rain_1}mm, last 24h: {rainModule.sum_rain_24}mm</p>
                <p>Inside temperature
                    is {mainStation.temperature} ({getRelativeTime(new Date(mainStation.ts * 1000))}) and humidity
                    is {mainStation.humidity}% CO2 {mainStation.co2}ppm noise {mainStation.noise}dB</p>
                <p>Pool House temperature
                    is {poolHouseModule.temperature} ({getRelativeTime(new Date(poolHouseModule.ts * 1000))}) and
                    humidity
                    is {poolHouseModule.humidity}%</p>
                <p>Home Office temperature
                    is {homeOfficeModule.temperature} ({getRelativeTime(new Date(homeOfficeModule.ts * 1000))}) and
                    humidity
                    is {homeOfficeModule.humidity}%</p>
                <p>Bedroom temperature
                    is {bedroomModule.temperature} ({getRelativeTime(new Date(bedroomModule.ts * 1000))}) and
                    humidity
                    is {bedroomModule.humidity}%</p>
                <p>Pellets heating is {therm.boiler_status ? "ON" : "OFF"} boost: {therm.boiler_valve_comfort_boost ? "ON" : "OFF"}</p>
                <p>Message: {msg}</p>
            </header>
        </div>
    );
}

export default App;

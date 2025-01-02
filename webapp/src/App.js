import logo from './logo.svg';
import './App.css';
import {useEffect, useState} from "react";

function App() {

    const [homesData, setHomesData] = useState({})
    const [msg, setMsg] = useState("");

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
                .then(responseJson => {
                    console.dir(responseJson);
                    setHomesData(responseJson);
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
                <img src={logo} className="App-logo" alt="logo"/>
                <p>Message: {msg}</p>
                <p>
                    Edit <code>src/App.js</code> and save to reload.
                </p>
                <a
                    className="App-link"
                    href="https://reactjs.org"
                    target="_blank"
                    rel="noopener noreferrer"
                >
                    Learn React
                </a>
            </header>
        </div>
    );
}

export default App;

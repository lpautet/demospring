package net.pautet.softs.demospring.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.function.Function;

/**
 * Provides AI function calling tools for Netatmo weather data access.
 * These functions allow the AI to query real-time weather data from Netatmo devices.
 */
@Slf4j
@Service
public class NetatmoFunctions {

    private final RedisUserService redisUserService;
    private final NetatmoApiService netatmoApiService;

    public NetatmoFunctions(RedisUserService redisUserService, NetatmoApiService netatmoApiService) {
        this.redisUserService = redisUserService;
        this.netatmoApiService = netatmoApiService;
    }

    /**
     * Request object for getting homes data
     */
    @JsonClassDescription("Request to get Netatmo homes data including all devices and modules")
    public record GetHomesDataRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The username/user ID to query data for")
            String username
    ) {}

    /**
     * Request object for getting home status
     */
    @JsonClassDescription("Request to get real-time status of a specific Netatmo home")
    public record GetHomeStatusRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The username/user ID to query data for")
            String username,
            
            @JsonProperty(required = true)
            @JsonPropertyDescription("The home ID to get status for")
            String homeId
    ) {}

    /**
     * Request object for getting measurements
     */
    @JsonClassDescription("Request to get historical measurements from a Netatmo device or module")
    public record GetMeasureRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The username/user ID to query data for")
            String username,
            
            @JsonProperty(required = true)
            @JsonPropertyDescription("The device ID (main station ID)")
            String deviceId,
            
            @JsonProperty(required = true)
            @JsonPropertyDescription("The module ID to get measurements from")
            String moduleId,
            
            @JsonProperty(required = true)
            @JsonPropertyDescription("Measurement types to retrieve (e.g., Temperature, Humidity, CO2, Pressure, Noise)")
            String[] types
    ) {}

    /**
     * Get Netatmo homes data - includes all devices, modules, and their current readings
     */
    @Bean
    @Description("Get all Netatmo homes data including devices, modules, rooms, and their configuration. Returns JSON with home structure, device types, module types, and basic information.")
    public Function<GetHomesDataRequest, String> getHomesData() {
        return request -> {
            try {
                log.info("AI calling getHomesData for user: {}", request.username());
                User user = redisUserService.findByUsername(request.username());
                if (user == null || user.getAccessToken() == null) {
                    return "{\"error\": \"User not found or not authenticated with Netatmo\"}";
                }
                
                String result = netatmoApiService.getHomesData(user);
                log.debug("getHomesData result: {}", result);
                return result;
            } catch (Exception e) {
                log.error("Error in getHomesData function: {}", e.getMessage(), e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        };
    }

    /**
     * Get real-time home status including current temperature, humidity, etc.
     */
    @Bean
    @Description("Get real-time status of a specific Netatmo home by home ID. Returns JSON with current sensor readings (temperature, humidity, CO2, pressure), device status, and room information.")
    public Function<GetHomeStatusRequest, String> getHomeStatus() {
        return request -> {
            try {
                log.info("AI calling getHomeStatus for user: {} homeId: {}", request.username(), request.homeId());
                User user = redisUserService.findByUsername(request.username());
                if (user == null || user.getAccessToken() == null) {
                    return "{\"error\": \"User not found or not authenticated with Netatmo\"}";
                }
                
                String result = netatmoApiService.getHomeStatus(user, request.homeId());
                log.debug("getHomeStatus result: {}", result);
                return result;
            } catch (Exception e) {
                log.error("Error in getHomeStatus function: {}", e.getMessage(), e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        };
    }

    /**
     * Get historical measurements from a specific module
     */
    @Bean
    @Description("Get historical measurements from a Netatmo device or module. Returns time-series data for the requested measurement types (Temperature, Humidity, CO2, Pressure, Noise, Rain) over the last 24 hours in 30-minute intervals.")
    public Function<GetMeasureRequest, String> getMeasure() {
        return request -> {
            try {
                log.info("AI calling getMeasure for user: {} device: {} module: {} types: {}", 
                        request.username(), request.deviceId(), request.moduleId(), String.join(",", request.types()));
                User user = redisUserService.findByUsername(request.username());
                if (user == null || user.getAccessToken() == null) {
                    return "{\"error\": \"User not found or not authenticated with Netatmo\"}";
                }
                
                String result = netatmoApiService.getMeasure(user, request.deviceId(), request.moduleId(), request.types());
                log.debug("getMeasure result: {}", result);
                return result;
            } catch (Exception e) {
                log.error("Error in getMeasure function: {}", e.getMessage(), e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        };
    }
}

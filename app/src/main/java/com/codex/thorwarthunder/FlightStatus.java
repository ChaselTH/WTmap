package com.codex.thorwarthunder;

import org.json.JSONObject;

import java.util.Iterator;

final class FlightStatus {
    String army = "";
    String vehicleType = "";
    boolean isAircraft;

    boolean hasAoa;
    boolean hasAos;
    boolean hasNy;
    boolean hasPitch;
    boolean hasRoll;
    boolean hasVerticalSpeed;
    boolean hasAltitude;
    boolean hasTas;
    boolean hasIas;
    boolean hasMach;
    boolean hasThrottle;
    boolean hasWepTime;
    boolean hasThrust;
    boolean hasRpm;
    boolean hasOilTemp;
    boolean hasWaterTemp;
    boolean hasFuel;
    boolean hasFuelPercent;
    boolean hasFuelConsume;
    boolean hasFuelTime;
    boolean hasNozzleAngle;
    boolean hasFlaps;
    boolean hasGear;
    boolean hasAirbrake;

    double aoa;
    double aos;
    double ny;
    double pitchDeg;
    double rollDeg;
    double verticalSpeed;
    double altitudeM;
    double tasKmh;
    double iasKmh;
    double mach;
    double throttlePercent;
    double wepSeconds;
    double thrustKg;
    double rpm;
    double oilTemp;
    double waterTemp;
    double fuelKg;
    double fuelPercent;
    double fuelConsume;
    double fuelMinutes;
    double nozzleAngle;
    double flapsPercent;
    double gearPercent;
    double airbrakePercent;

    static FlightStatus from(JSONObject state, JSONObject indicators) {
        FlightStatus status = new FlightStatus();
        status.army = optString(indicators, "army");
        status.vehicleType = optString(indicators, "type");

        status.aoa = firstFinite(
                optDouble(state, "AoA, deg"),
                optDouble(indicators, "aoa")
        );
        status.hasAoa = isFinite(status.aoa);
        status.isAircraft = "air".equalsIgnoreCase(status.army) || (status.army.length() == 0 && status.hasAoa);

        status.aos = optDouble(state, "AoS, deg");
        status.hasAos = isFinite(status.aos);

        status.ny = optDouble(state, "Ny");
        status.hasNy = isFinite(status.ny);

        status.pitchDeg = -optDouble(indicators, "aviahorizon_pitch");
        status.hasPitch = isFinite(status.pitchDeg) && Math.abs(status.pitchDeg) <= 90.0;

        status.rollDeg = normalizeRollDegrees(firstFinite(
                optDouble(indicators, "aviahorizon_roll"),
                -optDouble(indicators, "bank")
        ));
        status.hasRoll = isFinite(status.rollDeg) && Math.abs(status.rollDeg) <= 180.0;

        status.verticalSpeed = firstFinite(
                optDouble(state, "Vy, m/s"),
                optDouble(indicators, "vario")
        );
        status.hasVerticalSpeed = isFinite(status.verticalSpeed);

        status.altitudeM = firstFinite(
                optDouble(state, "H, m"),
                firstFinite(optDouble(indicators, "altitude_hour"), optDouble(indicators, "altitude_10k"))
        );
        status.hasAltitude = isFinite(status.altitudeM);

        status.tasKmh = firstFinite(
                optDouble(state, "TAS, km/h"),
                optDouble(indicators, "speed")
        );
        status.hasTas = isFinite(status.tasKmh);

        status.iasKmh = firstFinite(
                optDouble(state, "IAS, km/h"),
                optDouble(indicators, "speed_01")
        );
        status.hasIas = isFinite(status.iasKmh);

        status.mach = firstFinite(
                optDouble(state, "M"),
                optDouble(indicators, "mach")
        );
        status.hasMach = isFinite(status.mach);

        status.throttlePercent = firstFinite(
                optDouble(state, "throttle 1, %"),
                optDouble(indicators, "throttle") * 100.0
        );
        status.hasThrottle = isFinite(status.throttlePercent);

        status.wepSeconds = firstFinite(
                firstFinite(
                        firstFinite(
                                optDouble(state, "WEP time, s"),
                                optDouble(state, "WEP remaining, s")
                        ),
                        firstFinite(
                                optDouble(indicators, "wep_time"),
                                optDouble(indicators, "wep_remaining")
                        )
                ),
                firstFinite(findWepTimeSeconds(state), findWepTimeSeconds(indicators))
        );
        status.hasWepTime = isFinite(status.wepSeconds) && status.wepSeconds >= 0.0;

        double thrust = 0.0;
        boolean hasAnyThrust = false;
        for (int i = 1; i <= 8; i++) {
            double value = optDouble(state, "thrust " + i + ", kgs");
            if (isFinite(value)) {
                thrust += value;
                hasAnyThrust = true;
            }
        }
        status.thrustKg = thrust;
        status.hasThrust = hasAnyThrust;

        status.rpm = firstFinite(
                optDouble(state, "RPM 1"),
                optDouble(indicators, "rpm")
        );
        status.hasRpm = isFinite(status.rpm);

        status.oilTemp = optDouble(state, "oil temp 1, C");
        status.hasOilTemp = isFinite(status.oilTemp);

        status.waterTemp = optDouble(state, "water temp 1, C");
        status.hasWaterTemp = isFinite(status.waterTemp);

        status.fuelKg = firstFinite(
                optDouble(state, "Mfuel, kg"),
                optDouble(indicators, "fuel")
        );
        status.hasFuel = isFinite(status.fuelKg);

        double maxFuelKg = optDouble(state, "Mfuel0, kg");
        if (status.hasFuel && isFinite(maxFuelKg) && maxFuelKg > 0.0) {
            status.fuelPercent = status.fuelKg * 100.0 / maxFuelKg;
            status.hasFuelPercent = true;
        }

        status.fuelConsume = optDouble(indicators, "fuel_consume");
        status.hasFuelConsume = isFinite(status.fuelConsume);
        if (status.hasFuel && status.hasFuelConsume && status.fuelKg > 0.0 && status.fuelConsume > 0.01) {
            status.fuelMinutes = status.fuelKg / status.fuelConsume;
            status.hasFuelTime = true;
        }

        status.nozzleAngle = optDouble(indicators, "nozzle_angle");
        status.hasNozzleAngle = isFinite(status.nozzleAngle);

        status.flapsPercent = firstFinite(
                optDouble(state, "flaps, %"),
                optDouble(indicators, "flaps") * 100.0
        );
        status.hasFlaps = isFinite(status.flapsPercent);

        status.gearPercent = firstFinite(
                optDouble(state, "gear, %"),
                optDouble(indicators, "gears") * 100.0
        );
        status.hasGear = isFinite(status.gearPercent);

        status.airbrakePercent = firstFinite(
                optDouble(state, "airbrake, %"),
                optDouble(indicators, "airbrake_lever") * 100.0
        );
        status.hasAirbrake = isFinite(status.airbrakePercent);

        return status;
    }

    double thrustKn() {
        return thrustKg * 9.80665 / 1000.0;
    }

    private static double optDouble(JSONObject object, String key) {
        if (object == null || !object.has(key)) {
            return Double.NaN;
        }
        try {
            return object.getDouble(key);
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    private static String optString(JSONObject object, String key) {
        if (object == null || !object.has(key)) {
            return "";
        }
        return object.optString(key, "");
    }

    private static double findWepTimeSeconds(JSONObject object) {
        if (object == null) {
            return Double.NaN;
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String lower = key.toLowerCase();
            boolean mentionsWep = lower.contains("wep") || lower.contains("afterburn");
            boolean mentionsTime = lower.contains("time") || lower.contains("remain") || lower.contains("sec") || lower.endsWith(", s");
            if (mentionsWep && mentionsTime) {
                double value = optDouble(object, key);
                if (isFinite(value)) {
                    return value;
                }
            }
        }
        return Double.NaN;
    }

    private static double firstFinite(double first, double second) {
        return isFinite(first) ? first : second;
    }

    private static double normalizeRollDegrees(double value) {
        if (!isFinite(value)) {
            return Double.NaN;
        }
        double normalized = value % 360.0;
        if (normalized > 180.0) {
            normalized -= 360.0;
        } else if (normalized < -180.0) {
            normalized += 360.0;
        }
        return normalized;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}

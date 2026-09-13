package com.parkingsystem.dto.user;

import com.parkingsystem.entity.User;
import lombok.Data;

@Data
public class UserUpdateRequest {
    private String name;
    private User.Role role;
    private User.VehicleType defaultVehicleType;
}

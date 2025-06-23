package com.pawsql.mcp.service;

import com.pawsql.mcp.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Service for managing user permissions and access control.
 * Provides methods for checking if a user has permission to access a resource.
 */
@Service
public class PermissionService {
    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
    
    private final AuditLogService auditLogService;
    
    // Map of resource permissions by user email
    private final Map<String, Map<String, Set<String>>> userPermissions = new HashMap<>();
    
    // Default admin users, configurable via application.properties
    @Value("${pawsql.admin.users:}")
    private String[] adminUsers;
    
    // Default read-only users, configurable via application.properties
    @Value("${pawsql.readonly.users:}")
    private String[] readOnlyUsers;
    
    public PermissionService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }
    
    /**
     * Initialize default permissions for admin and read-only users.
     */
    public void initializeDefaultPermissions() {
        // Set admin permissions
        for (String adminUser : adminUsers) {
            if (adminUser != null && !adminUser.isEmpty()) {
                Map<String, Set<String>> permissions = new HashMap<>();
                permissions.put("workspace", new HashSet<>(Arrays.asList("read", "write", "delete")));
                permissions.put("sql", new HashSet<>(Arrays.asList("read", "write", "optimize")));
                permissions.put("admin", new HashSet<>(Arrays.asList("read", "manage_users")));
                userPermissions.put(adminUser, permissions);
                log.info("Added admin permissions for user: {}", adminUser);
            }
        }
        
        // Set read-only permissions
        for (String readOnlyUser : readOnlyUsers) {
            if (readOnlyUser != null && !readOnlyUser.isEmpty()) {
                Map<String, Set<String>> permissions = new HashMap<>();
                permissions.put("workspace", new HashSet<>(Arrays.asList("read")));
                permissions.put("sql", new HashSet<>(Arrays.asList("read")));
                userPermissions.put(readOnlyUser, permissions);
                log.info("Added read-only permissions for user: {}", readOnlyUser);
            }
        }
    }
    
    /**
     * Check if a user has permission to access a resource.
     * 
     * @param session User session
     * @param resource Resource being accessed
     * @param permission Permission being checked
     * @param clientIp Client IP address for audit logging
     * @return true if the user has permission, false otherwise
     */
    public boolean hasPermission(UserSession session, String resource, String permission, String clientIp) {
        if (session == null) {
            auditLogService.logSecurityEvent("PERMISSION_DENIED", "No session provided for permission check", clientIp);
            return false;
        }
        
        String email = session.getEmail();
        
        // Check if user has specific permissions
        Map<String, Set<String>> userResourcePermissions = userPermissions.get(email);
        boolean hasPermission = false;
        
        if (userResourcePermissions != null) {
            // Check if user has specific permission for the resource
            Set<String> resourcePermissions = userResourcePermissions.get(resource);
            if (resourcePermissions != null && resourcePermissions.contains(permission)) {
                hasPermission = true;
            }
        } else {
            // Default permissions for users not explicitly configured
            if ("workspace".equals(resource)) {
                // By default, all authenticated users can read and write workspaces
                hasPermission = "read".equals(permission) || "write".equals(permission);
            } else if ("sql".equals(resource)) {
                // By default, all authenticated users can read, write, and optimize SQL
                hasPermission = "read".equals(permission) || "write".equals(permission) || "optimize".equals(permission);
            } else {
                // No default permissions for other resources
                hasPermission = false;
            }
        }
        
        // Log permission check
        auditLogService.logPermissionCheck(session, resource, permission, hasPermission, clientIp);
        
        return hasPermission;
    }
    
    /**
     * Check if a user has permission to access a resource.
     * 
     * @param session User session
     * @param resource Resource being accessed
     * @param permission Permission being checked
     * @return true if the user has permission, false otherwise
     * @throws SecurityException if the user does not have permission
     */
    public void checkPermission(UserSession session, String resource, String permission) {
        String clientIp = "unknown";
        boolean hasPermission = hasPermission(session, resource, permission, clientIp);
        
        if (!hasPermission) {
            String errorMessage = String.format("User %s does not have %s permission for resource %s", 
                    session != null ? session.getEmail() : "unknown", permission, resource);
            log.warn(errorMessage);
            throw new SecurityException(errorMessage);
        }
    }
    
    /**
     * Set permissions for a user.
     * 
     * @param email User's email
     * @param resource Resource to set permissions for
     * @param permissions Set of permissions to grant
     */
    public void setPermissions(String email, String resource, Set<String> permissions) {
        userPermissions.computeIfAbsent(email, k -> new HashMap<>()).put(resource, permissions);
        log.info("Set permissions for user {}, resource {}: {}", email, resource, permissions);
    }
    
    /**
     * Add a permission for a user.
     * 
     * @param email User's email
     * @param resource Resource to add permission for
     * @param permission Permission to add
     */
    public void addPermission(String email, String resource, String permission) {
        userPermissions.computeIfAbsent(email, k -> new HashMap<>())
                .computeIfAbsent(resource, k -> new HashSet<>())
                .add(permission);
        log.info("Added permission for user {}, resource {}: {}", email, resource, permission);
    }
    
    /**
     * Remove a permission from a user.
     * 
     * @param email User's email
     * @param resource Resource to remove permission from
     * @param permission Permission to remove
     */
    public void removePermission(String email, String resource, String permission) {
        Map<String, Set<String>> resourcePermissions = userPermissions.get(email);
        if (resourcePermissions != null) {
            Set<String> permissions = resourcePermissions.get(resource);
            if (permissions != null) {
                permissions.remove(permission);
                log.info("Removed permission for user {}, resource {}: {}", email, resource, permission);
            }
        }
    }
}

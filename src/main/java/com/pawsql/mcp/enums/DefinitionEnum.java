package com.pawsql.mcp.enums;

public enum DefinitionEnum {

    MySQL("01", "MySQL", "435bb9a5-7887-4809-aa58-28c27df0d7ad", true, "mysql", "jdbc:mysql://", "com.mysql.cj.jdbc.Driver"),
    Postgres("02", "Postgres", "decd338e-5647-4c0b-adf4-da0e75f5a750", true, "postgres", "jdbc:postgresql://", "org.postgresql.Driver"),
    OpenGauss("03", "OpenGauss", "b0ed57dc-1575-0c10-be29-74ef4dbf2267", true, "opengauss", "jdbc:opengauss://", "org.opengauss.Driver"),
    Oracle("06", "Oracle", "fc1b699a-83cf-4ec2-add8-fb69e45cee00", true, "oracle", "jdbc:oracle:thin:@//", "oracle.jdbc.OracleDriver"),
    KingbaseEs("07", "电科金仓(Kingbase)", "09d38ac7-f73e-4189-b7f9-aa762af5f35a", true, "kingbase", "jdbc:kingbase8://", "com.kingbase8.Driver"),
    MogDB("14", "MogDB", "0f4092c8-d8ff-4803-9d40-7c684819ad8d", true, "opengauss", "jdbc:opengauss://", "org.opengauss.Driver"),
    Gauss_DB_Distributed("15", "GaussDB(分布式)", "19b3e5d4-613c-4561-be93-6aaef56dac48", true, "gaussdbx", "jdbc:opengauss://", "org.opengauss.Driver"),
    Gauss_Db_DWS("10", "GaussDB for DWS", "148df0e4-7475-4358-bcc8-da9db9c9c623", true, "dws", "jdbc:opengauss://", "org.opengauss.Driver");

    private String code;
    private String name;
    private String defId;
    private Boolean validateFlag;
    private String dbType;
    private String jdbcTemplate;
    private String driver;

    private DefinitionEnum(String code, String name, String defId, Boolean validateFlag, String dbType, String jdbcTemplate, String driver) {
        this.code = code;
        this.name = name;
        this.defId = defId;
        this.validateFlag = validateFlag;
        this.dbType = dbType;
        this.jdbcTemplate = jdbcTemplate;
        this.driver = driver;
    }

    public static DefinitionEnum match(String key) {
        DefinitionEnum result = DefinitionEnum.MySQL;

        for (DefinitionEnum s : values()) {
            if (s.getDefId().equals(key)) {
                result = s;
                break;
            }
        }
        return result;
    }

    public static DefinitionEnum matchByName(String name) {
        DefinitionEnum result = DefinitionEnum.MySQL;

        for (DefinitionEnum s : values()) {
            if (s.getName().equalsIgnoreCase(name)) {
                result = s;
                break;
            }
        }
        return result;
    }

    public static DefinitionEnum matchByDbType(String dbType) {
        DefinitionEnum result = null;
        for (DefinitionEnum s : values()) {
            if (s.getDbType().equals(dbType)) {
                result = s;
                break;
            }
        }
        return result;
    }

    public static DefinitionEnum catchMessage(String msg) {
        DefinitionEnum result = DefinitionEnum.MySQL;

        for (DefinitionEnum s : values()) {
            if (s.getName().equals(msg)) {
                result = s;
                break;
            }
        }
        return result;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public Boolean getValidateFlag() {
        return validateFlag;
    }

    public void setValidateFlag(Boolean validateFlag) {
        this.validateFlag = validateFlag;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDefId() {
        return defId;
    }

    public void setDefId(String defId) {
        this.defId = defId;
    }

    public String getJdbcTemplate() {
        return jdbcTemplate;
    }

    public void setJdbcTemplate(String jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }
}
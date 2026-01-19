package badgertech.putl.ideaplugle.naming

/**
 * 命名格式枚举
 */
enum class NamingFormat(val displayName: String, val description: String) {
    CAMEL_CASE("小驼峰", "userName, getAccount"),
    PASCAL_CASE("大驼峰", "UserName, GetAccount"),
    SNAKE_CASE("下划线", "user_name, get_account"),
    KEBAB_CASE("短横线", "user-name, get-account"),
    SCREAMING_SNAKE_CASE("常量下划线", "USER_NAME, GET_ACCOUNT"),
    DOT_CASE("点分隔", "user.name, get.account");

    companion object {
        fun getAll(): Array<NamingFormat> = entries.toTypedArray()
    }
}

package com.ibaevzz.bdev

object Constants {

    val RESULT_TYPE = arrayOf("INVALID SERIAL PORT",
        "CRC PASSED",
        "CRC FAILED",
        "TIMEOUT",
        "INVALID INPUT",
        "POINTLESS",
        "NOT SUPPORTED")

    val TVH_DEVICE = setOf(297 to "Пульсар модуль счетчика воды v6",
        319 to "Пульсар модуль счетчика воды v1.1",
        338 to "Пульсар модуль счетчика воды v1.9",
        385 to "Пульсар модуль счетчика воды Mini v1",
        408 to "Пульсар модуль счетчика воды v1.20",
        98  to "Пульсар водосчётчик RS485")

    val CURSED_IDS = arrayOf(297, 98)

    val PULSAR_ERROR = arrayOf("Успех",
        "Отсутствует запрашиваемый код функции",
        "Ошибка в битовой маске запроса",
        "Ошибочная длинна запроса",
        "Отсутствует параметр",
        "Запись заблокирована, требуется авторизация",
        "Записываемое значение (параметр) находится вне заданного диапазона",
        "Отсутствует запрашиваемый тип архива",
        "Превышение максимального количества архивных значений за один пакет")

    val ADDRESS_REQUEST = arrayOf(
        byteArrayOf(0xf0.toByte(), 0x0f, 0x0f, 0xf0.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x00),
        byteArrayOf(0x00, 0x00, 0x00, 0x00,
            0x0a, 0x0c, 0x01, 0x00, 0x01, 0x00),
        byteArrayOf(0x0a, 0x0c, 0x00, 0x00, 0x01, 0x00),
        byteArrayOf(0x03, 0x02, 0x46, 0x00, 0x01)
    )

    val PASSWORD_REQUEST = arrayOf(
        byteArrayOf(0x0b, 0x14, 0x00, 0xe0.toByte()),
        byteArrayOf(0x0b, 0x14, 0x99.toByte(), 0x00)
    )

    val WRITE_VALUE_REQUEST = byteArrayOf(0x03, 0x12,
        0x01, 0x00, 0x00, 0x00)

    val WRITE_ADDRESS_REQUEST = arrayOf(
        byteArrayOf(0x0b, 0x14, 0x01, 0x00),
        byteArrayOf(0x0b, 0x14, 0x02, 0x00),
        byteArrayOf(0x0b, 0x14, 0xf2.toByte(),
            0xe7.toByte(), 0xd2.toByte(), 0x0a,
            0x1f, 0xeb.toByte(), 0x8c.toByte(),
            0xa9.toByte(), 0x54, 0xab.toByte())
    )

    val GET_DEVICE_ID_REQUEST = arrayOf(
        byteArrayOf(0x0a, 0x0c, 0x00, 0x00, 0x01, 0x00),
        byteArrayOf(0x03, 0x02, 0x46, 0x00, 0x01)
    )

    val GET_VALUE_REQUEST = byteArrayOf(0x01, 0x0e, 0x01, 0x00, 0x00, 0x00)

    val GET_ADDRESS_REQUEST = arrayOf(
        byteArrayOf(0x0a, 0x0c, 0x01, 0x00),
        byteArrayOf(0x0a, 0x0c, 0x02, 0x00)
    )

    val GET_VERSION_REQUEST = arrayOf(
        byteArrayOf(0x0a, 0x0c, 0x02, 0x00),
        byteArrayOf(0x0a, 0x0c, 0x05, 0x00)
    )

    val PULSAR_PASSWORDS = arrayOf(31285, 3791, 48053, 45182)
}
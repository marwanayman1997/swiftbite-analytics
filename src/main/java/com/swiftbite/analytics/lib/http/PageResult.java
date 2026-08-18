package com.swiftbite.analytics.lib.http;

import java.util.List;

public record PageResult<T>(List<T> data, PaginationMeta meta) {
}

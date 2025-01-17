/*
 * MIT License
 *
 * Copyright (c) 2017-2019 RiiConnect24 and its contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package xyz.rc24.bot.core.entities;

/**
 * Log type enum
 *
 * @author Artuto
 */

public enum LogType
{
    MOD("mod", "Moderation log. Shows bans/unbans.", "modlog_id"),
    SERVER("srv", "Server log. Shows bans/unbans, along with joins and leaves.", "serverlog_id"),

    UNKNOWN(null, null, null);

    private final String code, description, column;

    LogType(String code, String description, String column)
    {
        this.code = code;
        this.description = description;
        this.column = column;
    }

    public String getCode()
    {
        return code;
    }

    public String getDescription()
    {
        return description;
    }

    public String getColumn()
    {
        return column;
    }

    public static LogType fromCode(String code)
    {
        for(LogType type : values())
        {
            if(code.equals(type.getCode()))
                return type;
        }

        return UNKNOWN;
    }
}

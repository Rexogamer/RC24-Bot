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
 * Code type enum
 *
 * @author Artuto
 */

public enum CodeType
{
    GAME(0, "games", "Game", "🎮 **Games**", "game"),
    NNID(1, "nnid", "Nintendo Network ID", "<:NintendoNetworkID:287655797104836608> **Nintendo Network ID**", "nnid"),
    PSN(2, "psn", "PlayStation Network ID", "<:psn:545097818319224832> **PlayStation Network ID**", "psn"),
    SWITCH(3, "switch", "Switch", "<:Switch:287652338791874560> **Switch**", "switch"),
    THREEDS(4, "threeds", "3DS", "<:New3DSXL:287651327763283968> **3DS**", "3ds"),
    WII(5, "wii", "Wii", "<:Wii:259081748007223296> **Wii**", "wii"),

    UNKNOWN(-1, null, null, null, null);

    private final int id;
    private final String column, displayName, formattedName, name;

    CodeType(int id, String column, String displayName, String formattedName, String name)
    {
        this.id = id;
        this.column = column;
        this.displayName = displayName;
        this.formattedName = formattedName;
        this.name = name;
    }

    public int getId()
    {
        return id;
    }

    public String getColumn()
    {
        return column;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public String getFormattedName()
    {
        return formattedName;
    }

    public String getName()
    {
        return name;
    }

    public static CodeType fromCode(String code)
    {
        for(CodeType type : values())
        {
            if(code.equalsIgnoreCase(type.getName()))
                return type;
        }

        return UNKNOWN;
    }

    public static CodeType fromId(int id)
    {
        for(CodeType type : values())
        {
            if(type.getId() == id)
                return type;
        }

        return UNKNOWN;
    }
}

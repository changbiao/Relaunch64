/*
 * Relaunch64 - A Java cross-development IDE for C64 machine language coding.
 * Copyright (C) 2001-2014 by Daniel Lüdecke (http://www.danielluedecke.de)
 * 
 * Homepage: http://www.popelganda.de
 * 
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Dieses Programm ist freie Software. Sie können es unter den Bedingungen der GNU
 * General Public License, wie von der Free Software Foundation veröffentlicht, weitergeben
 * und/oder modifizieren, entweder gemäß Version 3 der Lizenz oder (wenn Sie möchten)
 * jeder späteren Version.
 * 
 * Die Veröffentlichung dieses Programms erfolgt in der Hoffnung, daß es Ihnen von Nutzen sein 
 * wird, aber OHNE IRGENDEINE GARANTIE, sogar ohne die implizite Garantie der MARKTREIFE oder 
 * der VERWENDBARKEIT FÜR EINEN BESTIMMTEN ZWECK. Details finden Sie in der 
 * GNU General Public License.
 * 
 * Sie sollten ein Exemplar der GNU General Public License zusammen mit diesem Programm 
 * erhalten haben. Falls nicht, siehe <http://www.gnu.org/licenses/>.
 */
package de.relaunch64.popelganda.assemblers;

import de.relaunch64.popelganda.assemblers.ErrorHandler.ErrorInfo;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Soci/Singular
 */
class Assembler_tmpx implements Assembler
{
    final static String opcodes[] = { // must be sorted!
        "ADC", "AHX", "ALR", "ANC", "AND", "ARR", "ASL", "AXS", "BCC", "BCS",
        "BEQ", "BIT", "BMI", "BNE", "BPL", "BRK", "BVC", "BVS", "CLC", "CLD",
        "CLI", "CLV", "CMP", "CPX", "CPY", "DCP", "DEC", "DEX", "DEY", "EOR",
        "INC", "INX", "INY", "ISC", "JMP", "JSR", "LAS", "LAX", "LDA", "LDX",
        "LDY", "LSR", "NOP", "ORA", "PHA", "PHP", "PLA", "PLP", "RLA", "ROL",
        "ROR", "RRA", "RTI", "RTS", "SAX", "SBC", "SEC", "SED", "SEI", "SHX",
        "SHY", "SLO", "SRE", "STA", "STX", "STY", "TAS", "TAX", "TAY", "TSX",
        "TXA", "TXS", "TYA", "XAA"
    };

    @Override
    public String name() {
        return "TMPx";
    }

    @Override
    public String fileName() {
        return "tmpx";
    }
    /**
     * Assembler ID.
     * @return the unique assembler ID.
     */
    @Override
    public int getID() {
        return Assemblers.ID_TMPX;
    }

    @Override
    public String syntaxFile() {
        return "assembly-tmpx.xml";
    }

    @Override
    public String getLineComment() {
        return ";";
    }

    @Override
    public String getMacroPrefix() {
        return ".";
    }

    @Override
    public String getByteDirective() {
        return ".byte";
    }

    @Override
    public String getBasicStart(int start) {
        return "*= $801\n.block\n.word s, 10\n.byte $9e\n.null \"" + Integer.toString(start) + "\"\ns .word 0\n.bend\n";
    }

    @Override
    public String getIncludeSourceDirective(String path) {
        return ".include \"" + path + "\"";
    }

    @Override
    public String getIncludeTextDirective(String path) {
        return ".binary \"" + path + "\"";
    }

    @Override
    public String getIncludeC64Directive(String path) {
        return ".binary \"" + path + "\",2";
    }

    @Override
    public String getIncludeBinaryDirective(String path) {
        return ".binary \"" + path + "\"";
    }

    @Override
    public String[] getScriptKeywords() {
        return new String[] {};
    }

    @Override
    public String getDefaultCommandLine(String fp) {
        return fp + " -i " + INPUT_FILE + " -o " + OUTPUT_FILE;
    }

    @Override
    public String getHelpCLI() {
        return "";
    }

    /**
     * Extracts all labels, functions and macros of a source code file. Information
     * on names and linenumbers of labels, functions and macros are saved as linked
     * hashmaps. Information can then be accessed via 
     * {@link Assembler.labelList#labels labelList.labels},
     * {@link Assembler.labelList#functions labelList.functions} and
     * {@link Assembler.labelList#macros labelList.macros}.
     * 
     * @param lineReader a LineNumberReader from the source code content, which is
     * created in {@link de.relaunch64.popelganda.Editor.LabelExtractor#getLabels(java.lang.String, de.relaunch64.popelganda.assemblers.Assembler, int) LabelExtractor.getLabels()}.
     * @param lineNumber the line number, from where to start the search for labels/functions/macros.
     * use 0 to extract all labels/functions/macros. use any specific line number to extract only
     * global labels/functions/macros and local labels/functions/macros within scope.
     * @return a {@link Assembler.labelList labelList} 
     * with information (names and line numbers) about all extracted labels/functions/macros.
     */
    @Override
    public labelList getLabels(LineNumberReader lineReader, int lineNumber) {
        labelList returnValue = new labelList(null, null, null);
        LinkedHashMap<String, Integer> labelValues = new LinkedHashMap<>();
        Pattern p = Pattern.compile("^\\s*(?<label>[a-zA-Z][a-zA-Z0-9_]*\\b)?\\s*(?<directive>\\.(?:block|bend|macro|segment|endm)\\b)?.*");
        String line;
        LinkedList<LinkedHashMap<String, Integer>> scopes = new LinkedList<>(), myscope = new LinkedList<>();
        myscope.add(labelValues);
        try {
            while ((line = lineReader.readLine()) != null) {
                if (lineReader.getLineNumber() == lineNumber) {
                    myscope = (LinkedList)scopes.clone();
                    myscope.add(labelValues);
                }
                Matcher m = p.matcher(line);

                if (!m.matches()) continue;
                String label = m.group("label");

                if (label != null) {
                    if (label.length() == 3 && Arrays.binarySearch(opcodes, label.toUpperCase()) >= 0) continue;
                }

                String directive = m.group("directive");
                if (directive != null) {
                    switch (directive) {
                        case ".macro":
                        case ".segment":
                            if (label != null) returnValue.macros.put(label, lineReader.getLineNumber());
                            scopes.add(labelValues);labelValues = new LinkedHashMap<>();
                            break;
                        case ".block":
                            if (label != null) labelValues.put(label, lineReader.getLineNumber());
                            scopes.add(labelValues);labelValues = new LinkedHashMap<>();
                            break;
                        case ".endm":
                        case ".bend":
                            if (label != null) labelValues.put(label, lineReader.getLineNumber());
                            if (!scopes.isEmpty()) labelValues = scopes.removeLast();
                            break;
                    }
                    continue;
                }
                if (label != null) labelValues.put(label, lineReader.getLineNumber());
            }
            for (LinkedHashMap<String, Integer> map : myscope) {
                returnValue.labels.putAll(map);
            }
        }
        catch (IOException ex) {
        }
        return returnValue;
    }
    /**
     * Returns the label name part before the cursor.
     * 
     * @param line Currect line content
     * @param pos Caret position
     * 
     * @return Label name part before the cursor.
     */
    @Override
    public String labelGetStart(String line, int pos) {
        String line2 = new StringBuffer(line.substring(0, pos)).reverse().toString();
        Pattern p = Pattern.compile("(?i)([a-z0-9_]*[a-z]\\b).*");
        Matcher m = p.matcher(line2);
        if (!m.matches()) return "";
        return new StringBuffer(m.group(1)).reverse().toString();
    }
    /**
     * Parses the error messages from the error log and adds the information
     * to the {@link ErrorHandler.ErrorInfo}.
     * 
     * @param lineReader a LineNumberReader from the error log, which is created
     * by {@link ErrorHandler#readErrorLines(java.lang.String, de.relaunch64.popelganda.assemblers.Assembler) readErrorLines()}.
     * @return an ArrayList of {@link ErrorHandler.ErrorInfo} for
     * each logged error.
     */
    @Override
    public ArrayList<ErrorInfo> readErrorLines(LineNumberReader lineReader) {
        final ArrayList<ErrorInfo> errors = new ArrayList<>();
        String line;     //a.asm(4) : error 30: undefined label; 'i' //a.asm(4) : error 3: illegal mnemonic at col 14
        Pattern p = Pattern.compile("^(?<file>.*?)\\((?<line>\\d+)\\) : error \\d+: .*?( at col (?<col>\\d+))?");
        try {
            while ((line = lineReader.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (!m.matches()) continue;
                ErrorInfo e = new ErrorInfo(
                        Integer.parseInt(m.group("line")),
                        (m.group("col") == null) ? 1 : Integer.parseInt(m.group("col")),
                        lineReader.getLineNumber(), 1,
                        m.group("file")
                        );
                errors.add(e);
            }
        }
        catch (IOException ex) {
        }
        return errors;
    }

    private static final Pattern directivePattern = Pattern.compile("^\\s*(?:[a-zA-Z][a-zA-Z0-9_]*\\b)?\\s*(?<directive>\\.[a-zA-Z]+\\b)?.*");

    // folding according to compiler directives
    @Override
    public int getFoldLevel(String line, int foldLevel) {
        Matcher m = directivePattern.matcher(line);

        if (m.matches()) {
            String directive = m.group("directive");
            if (directive != null) {
                switch (directive.toLowerCase()) {
                case ".block":
                case ".macro":
                case ".segment":
                case ".if":
                case ".ifeq":
                case ".ifne":
                case ".ifpl":
                case ".ifmi":
                case ".ifdef":
                case ".ifndef":
                    return foldLevel + 1;
                case ".bend":
                case ".endm":
                case ".endif":
                    return foldLevel - 1;
                }
            }
        }
        return foldLevel;
    }
}

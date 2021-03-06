/*
 * Copyright (c) 2013, Jaroslav Bachorik <jaroslav.bachorik@oracle.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.sun.btrace.btracel;

import com.sun.btrace.CommandListener;
import com.sun.btrace.btracel.export.BTrace1JavaExporter;
import com.sun.btrace.btracel.model.Script;
import com.sun.btrace.btracel.model.compiler.BTraceLCompiler;
import com.sun.btrace.client.Client;
import com.sun.btrace.comm.Command;
import com.sun.btrace.comm.MessageCommand;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import sun.misc.Signal;
import sun.misc.SignalHandler;

/**
 *
 * @author Jaroslav Bachorik <jaroslav.bachorik@oracle.com>
 */
public class MainShort {
    public static void main(String[] args) throws Exception {
        OptionParser parser = new OptionParser();
        parser.accepts("x", "Show the BTrace java source. Must be used only in conjunction with -i option.");
        parser.accepts("i", "BTrace oneliner. Can not be used with -f option.").requiredIf("x").withRequiredArg().ofType(String.class);
        parser.accepts("f", "BTrace script file. Can not be used with -i option.").withRequiredArg().ofType(String.class);
        parser.accepts("?", "Help.");
        parser.accepts("h", "Help");
        
        OptionSet opts = parser.parse(args);

        if (opts.has("?") || opts.has("h")) {
            parser.printHelpOn(System.out);
            System.exit(0);
        }

        if (!opts.has("x") && opts.nonOptionArguments().isEmpty()) {
            throw new IllegalArgumentException("At least one of the parameters must be a PID");
        }

        String scriptPath = null;
        if (opts.has("i")) {
            if (opts.has("f")) {
                throw new IllegalArgumentException("Can not specify both -i and -f options");
            }
            
            String oneliner = (String)opts.valueOf("i");
            
            Script scr = new BTraceLCompiler().compile(oneliner);
            String s = scr.export(new BTrace1JavaExporter());
            
            if (opts.has("x")) {
                System.out.println(s);
                System.exit(0);
            }

            final Path temp = Files.createTempDirectory("btrace");
            final Path f = temp.resolve(scr.getName() + ".java");
            
            Files.write(f, s.getBytes("utf-8"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

            Signal.handle(new Signal("INT"), new SignalHandler() {
                @Override
                public void handle(Signal signal) {
                    try {
                        Files.deleteIfExists(f);
                        Files.deleteIfExists(temp);
                        System.exit(0);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

            scriptPath = f.toString();
        } else if (opts.has("f")) {
            scriptPath = (String)opts.valueOf("f");
        }

        final Client c = new Client(2020);
        final byte[] x = c.compile(scriptPath, ".", new PrintWriter(System.err));
        
        c.attach(opts.nonOptionArguments().get(0));

        c.submit(x, new String[0], new CommandListener() {
            @Override
            public void onCommand(Command cmnd) throws IOException {
                if (cmnd.getType() == Command.MESSAGE) {
                    MessageCommand msg = (MessageCommand)cmnd;
                    System.out.print(msg.getMessage());
                }
            }
        });   
    }
}

package org.cx4a.rsense;

import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import org.jruby.ast.Node;

import org.cx4a.rsense.ruby.Ruby;
import org.cx4a.rsense.ruby.IRubyObject;
import org.cx4a.rsense.ruby.Block;
import org.cx4a.rsense.typing.TypeSet;
import org.cx4a.rsense.typing.vertex.Vertex;
import org.cx4a.rsense.typing.runtime.SpecialMethod;
import org.cx4a.rsense.util.Logger;

public class CodeAssist {
    public static final String SUGGEST_COMPLETION_METHOD_NAME = "__rsense_suggest_completion__";

    public static class Context {
        public Project project;
        public TypeSet typeSet;
    }
    
    private org.jruby.Ruby rubyRuntime;
    private final Config config;
    private final Context context;

    private SpecialMethod suggestCompletionMethod = new SpecialMethod() {
            public void call(Ruby runtime, TypeSet receivers, Vertex[] args, Block blcck, Result result) {
                context.typeSet.addAll(receivers);
            }
        };

    public CodeAssist(Config config) {
        rubyRuntime = org.jruby.Ruby.newInstance(); // for parse
        this.config = config;
        context = new Context();
    }

    public LoadResult load(Project project, File file, String encoding) {
        try {
            InputStream in = new FileInputStream(file);
            try {
                return load(project, new InputStreamReader(in, encoding));
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return LoadResult.failWithException("Cannot open file", e);
        } 
    }

    public LoadResult load(Project project, Reader reader) {
        try {
            prepare(project);
            Node ast = parseString(readAll(reader));
            project.getGraph().createVertex(ast);

            LoadResult result = new LoadResult();
            result.setAST(ast);
            return result;
        } catch (IOException e) {
            return LoadResult.failWithException("Cannot load file", e);
        }
    }

    public SuggestCompletionResult suggestCompletion(Project project, File file, String encoding, long offset) {
        try {
            InputStream in = new FileInputStream(file);
            try {
                return suggestCompletion(project, new InputStreamReader(in, encoding), offset);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return SuggestCompletionResult.failWithException("Cannot open file", e);
        } 
    }
    public SuggestCompletionResult suggestCompletion(Project project, Reader reader, long offset) {
        try {
            prepare(project);
            Node ast = parseString(readAndInjectCode(reader, offset, SUGGEST_COMPLETION_METHOD_NAME, "."));
            project.getGraph().createVertex(ast);

            List<SuggestCompletionResult.CompletionCandidate> candidates = new ArrayList<SuggestCompletionResult.CompletionCandidate>();
            for (IRubyObject object : context.typeSet) {
                for (String name : object.getMetaClass().getMethods(true)) {
                    candidates.add(new SuggestCompletionResult.CompletionCandidate(name));
                }
            }

            SuggestCompletionResult result = new SuggestCompletionResult();
            result.setCandidates(candidates);
            return result;
        } catch (IOException e) {
            return SuggestCompletionResult.failWithException("Cannot read file", e);
        }
    }

    private void prepare(Project project) {
        context.project = project;
        context.typeSet = new TypeSet();

        project.getGraph().addSpecialMethod(SUGGEST_COMPLETION_METHOD_NAME, suggestCompletionMethod);
    }

    private String readAll(Reader reader) throws IOException {
        return readAndInjectCode(reader, -1, null, null);
    }

    private String readAndInjectCode(Reader reader, long offset, String injection, String prefix) throws IOException {
        char[] buf = new char[4096];
        int read;
        int len = 0;
        StringBuilder buffer = new StringBuilder();
        while ((read = reader.read(buf)) != -1) {
            int index = 0;
            if (offset >= 0 && len <= offset) {
                for (int i = 0; i < read; i++) {
                    if (Character.isHighSurrogate(buf[i])) {
                    } else {
                        len++;
                        if (len == offset) {
                            index = i + 1;
                            buffer.append(buf, 0, index);
                            if (!buffer.substring(Math.max(0, buffer.length() - prefix.length()), buffer.length()).equals(prefix)) {
                                buffer.append(prefix);
                            }
                            buffer.append(injection);
                            break;
                        }
                    }
                }
            }
            buffer.append(buf, index, read);
        }
        return buffer.toString();
    }

    public Node parseString(String string) {
        ByteArrayInputStream in = new ByteArrayInputStream(string.getBytes());
        return rubyRuntime.parseFromMain(in, "<codeassist>");
    }
}

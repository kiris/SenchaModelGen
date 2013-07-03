package localhost.apt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;

import localhost.annotation.Field;
import localhost.annotation.Model;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;


@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes("localhost.annotation.Model")
public class SenchaModelGen extends AbstractProcessor {
    private Template template;

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        if (this.setup()) {
            for (TypeElement annotation : annotations) {
                for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                    assert element instanceof TypeElement;
                    TypeElement typeElement = (TypeElement) element;

                    try {
                        Model model = element.getAnnotation(Model.class);
                        ModelGenerator generator = new ModelGenerator(this.processingEnv, model, typeElement);
                        ModelData modelData = generator.generate();
                        if (modelData != null) {
                            this.write(modelData);
                        }
                    } catch (Exception e) {
                        this.printError(e, typeElement);
                    }
                }
            }
        }
        return true;
    }

    private boolean setup() {
        try {
            TemplateLoader loader = new ClassPathTemplateLoader("/templates");
            Handlebars handlebars = new Handlebars(loader);
            this.template = handlebars.compile("model");
            
            return true;
        } catch (IOException e) {
            this.printError(e);
            return false;
        }
    }

    public void write(ModelData modelData) throws IOException {
        File dir = new File(modelData.getDir());
        File file = new File(dir, modelData.getFileName());
        dir.mkdirs();

        PrintWriter writer = new PrintWriter(new FileOutputStream(file));
        try {
            writer.println(template.apply(modelData));
    
            this.printNote(this.getClass().getName() + " generate: " + file.getPath());
        } finally {
            writer.close();
        }
    }


    private void printNote(String message) {
        this.processingEnv.getMessager().printMessage(Kind.NOTE, message);
    }

    private void printError(Exception e) {
        this.printError(e, null);
    }

    private void printError(Exception e, TypeElement typeElement) {
        this.processingEnv.getMessager().printMessage(Kind.ERROR, e.toString(), typeElement);
    }

    public static class ModelData {
        final String dir;
        final String fileName;

        final String className;
        final List<FieldData> fields;
        final Date date;

        public ModelData(String dir, String fileName, String className,
                List<FieldData> fields, Date date) {
            this.dir = dir;
            this.fileName = fileName;
            this.className = className;
            this.fields = fields;
            this.date = date;
        }

        public String getDir() {
            return dir;
        }
        public String getFileName() {
            return fileName;
        }
        public String getClassName() {
            return className;
        }
        public List<FieldData> getFields() {
            return fields;
        }
        public Date getDate() {
            return date;
        }
    }

    public static class FieldData {
        final String name;
        final String type;
        final boolean presence;

        public FieldData(String name, String type, boolean presence) {
            this.name = name;
            this.type = type;
            this.presence = presence;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public boolean getPresence() {
            return presence;
        }
    }

    static private class ModelGenerator {
        private final ProcessingEnvironment processingEnv;
        private final Model model;
        private final TypeElement element;

        public ModelGenerator(ProcessingEnvironment processingEnv, Model model,
                TypeElement element) {
            this.processingEnv = processingEnv;
            this.model = model;
            this.element = element;
        }

        private boolean isGenerable() {
            if (this.element.getKind() != ElementKind.CLASS) {
                return false;
            }
            if (this.element.getModifiers().contains(Modifier.ABSTRACT)) {
                return false;
            }
            return true;
        }

        public ModelData generate() {
            if (!this.isGenerable()) {
                return null;
            }

            return new ModelData(this.getDir(), this.getFileName(),
                    this.getAbsoluteName(), this.getFields(), new Date());
        }

        private List<FieldData> getFields() {
            List<VariableElement> fieldElements = ElementFilter.fieldsIn(this.element
                    .getEnclosedElements());
            ArrayList<FieldData> fields = new ArrayList<>(fieldElements.size());
            for (VariableElement fieldElement : fieldElements) {
                FieldGenerator generator = (new FieldGenerator(this.processingEnv, this.model, fieldElement));
                FieldData field = generator.generate();
                if (field != null) {
                    fields.add(field);
                }
            }

            return fields;
        }

        private String getAbsoluteName() {
            String namespace = this.getNameSpace();
            String modelName = this.getClassName();
            return namespace + (namespace.isEmpty() ? "" : ".") + modelName;
        }

        private String getDir() {
            return model.dir();
        }

        private String getFileName() {
            return this.getClassName() + ".js";
        }

        private String getNameSpace() {
            return model.namespace();
        }

        private String getClassName() {
            return model.prefix() + element.getSimpleName().toString() + model.suffix();
        }
    }

    static private class FieldGenerator {
        @SuppressWarnings("unused")
        private final ProcessingEnvironment processingEnv;
        @SuppressWarnings("unused")
        private final Model model;
        private final Field field;
        private final VariableElement element;

        public FieldGenerator(ProcessingEnvironment processingEnv, Model model,
                VariableElement element) {
            this.processingEnv = processingEnv;
            this.model = model;
            this.element = element;
            this.field = element.getAnnotation(Field.class);
        }

        public FieldData generate() {
            if (!this.isGenerable()) {
                return null;
            }
            String fieldName = this.getFieldName();
            String fieldType = this.getFieldType();
            boolean presence = this.isPresence();
            return new FieldData(fieldName, fieldType, presence);
        }

        private String getFieldType() {
            if (field != null && !field.type().isEmpty()) {
                return field.type();
            } else {
                return this.getFieldType(this.element.asType());
            }
        }

        private String getFieldType(TypeMirror type) {
            TypeKind kind = type.getKind();
            switch (kind) {
            case BOOLEAN:
                return "boolean";

            case BYTE:
            case SHORT:
            case INT:
            case LONG:
            case CHAR:
                return "int";

            case FLOAT:
            case DOUBLE:
                return "float";

            case DECLARED:
                try {
                    return this.getFieldType(processingEnv.getTypeUtils().unboxedType(type));
                } catch (IllegalArgumentException e) {
                    return null;
                }

            default:
                return null;
            }
        }

        private String getFieldName() {
            if (field != null && !field.name().isEmpty()) {
                return field.name();
            }
            return element.getSimpleName().toString();
        }

        private boolean isPresence() {
            if (field != null && field.required() != Field.Required.DEFAULT) {
                return Boolean.valueOf(field.required().toString().toLowerCase());
            }
            return element.asType().getKind().isPrimitive();
        }

        private boolean isGenerable() {
            if (element.getKind() != ElementKind.FIELD) {
                return false;
            }
            if (element.getModifiers().contains(Modifier.STATIC)) {
                return false;
            }
            if (field != null && field.exclude()) {
                return false;
            }
            return true;
        }

    }
}

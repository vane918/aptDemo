package com.vanelst.compiler;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.vanelst.annotation.ARouter;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * 注解处理器
 * @author WenYinghao
 * @date 2020-02-07
 * @Description
 */
// AutoService则是固定的写法，加个注解即可
// 通过auto-service中的@AutoService可以自动生成AutoService注解处理器，用来注册
// 用来生成 META-INF/services/javax.annotation.processing.Processor 文件
@AutoService(Processor.class)
@SupportedAnnotationTypes({"com.vanelst.annotation.ARouter"})
// 指定JDK编译版本
@SupportedSourceVersion(SourceVersion.RELEASE_7)
// 注解处理器接收的参数
@SupportedOptions("content")
public class ARouterProcessor extends AbstractProcessor {

    // 操作Element工具类 (类、函数、属性都是Element)
    private Elements elementUtils;

    // type(类信息)工具类，包含用于操作TypeMirror的工具方法
    private Types typeUtils;

    // Messager用来报告错误，警告和其他提示信息
    private Messager messager;

    // 文件生成器 类/资源，Filter用来创建新的源文件，class文件以及辅助文件
    private Filer filer;

    /**
     * 主要用于一些初始化的操作，通过该方法的参数ProcessingEnvironment可以获取一些列有用的工具类
     * @param processingEnvironment 当前或是之前的运行环境,可以通过该对象查找找到的注解。
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        // 父类受保护属性，可以直接拿来使用。
        // 其实就是init方法的参数ProcessingEnvironment
        elementUtils = processingEnvironment.getElementUtils();
        messager = processingEnvironment.getMessager();
        filer = processingEnvironment.getFiler();

        // 通过ProcessingEnvironment去获取build.gradle传过来的参数
        String content = processingEnvironment.getOptions().get("content");
        messager.printMessage(Diagnostic.Kind.NOTE, content);
    }

    /**
     * 相当于main函数，开始处理注解
     * 注解处理器的核心方法，处理具体的注解，生成Java文件
     * @param set 使用了支持处理注解的节点集合（类 上面写了注解）
     * @param roundEnvironment 当前或是之前的运行环境,可以通过该对象查找找到的注解
     * @return true 表示后续处理器不会再处理（已经处理完成）
     */
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        if (set.isEmpty()) return false;

        // 获取所有带ARouter注解的 类节点
        Set<? extends Element> elements = roundEnvironment.getElementsAnnotatedWith(ARouter.class);
        // 遍历所有类节点
        for (Element element : elements) {
            // 通过类节点获取包节点（全路径：com.vanelst.xxx）
            String packageName = elementUtils.getPackageOf(element).getQualifiedName().toString();
            // 获取简单类名
            String className = element.getSimpleName().toString();
            messager.printMessage(Diagnostic.Kind.NOTE, "被注解的类有：" + className);
            // 最终想生成的类文件名
            String finalClassName = className + "$ARouter";

            //传统写法
            //traditionalProcess(filer, element, packageName, finalClassName, className);
            //利用javapoet
            javapoetProcess(filer, element, packageName, finalClassName, className);

        }
        return true;
    }

    /**
     * 传统写法
     * @param filer
     * @param element
     * @param packageName
     * @param finalClassName
     * @param className
     */
    private void traditionalProcess(Filer filer, Element element, String packageName,
                                    String finalClassName, String className) {
        try {
            // 创建一个新的源文件（Class），并返回一个对象以允许写入它
            JavaFileObject sourceFile = filer.createSourceFile(packageName + "." + finalClassName);
            // 定义Writer对象，开启写入
            Writer writer = sourceFile.openWriter();
            // 设置包名
            writer.write("package " + packageName + ";\n");
            writer.write("public class " + finalClassName + " {\n");
            writer.write("public static Class<?> findTargetClass(String path) {\n");
            // 获取类之上@ARouter注解的path值
            ARouter aRouter = element.getAnnotation(ARouter.class);
            writer.write("if (path.equals(\"" + aRouter.path() + "\")) {\n");
            writer.write("return " + className + ".class;\n}\n");
            writer.write("return null;\n");
            writer.write("}\n}");
            // 最后结束别忘了
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 高级写法，javapoet构建工具
     * @param filer
     * @param element
     * @param packageName
     * @param finalClassName
     * @param className
     */
    private void javapoetProcess(Filer filer, Element element, String packageName,
                                    String finalClassName, String className) {
        try {
            // 获取类之上@ARouter注解的path值
            ARouter aRouter = element.getAnnotation(ARouter.class);

            // 构建方法体
            MethodSpec method = MethodSpec.methodBuilder("findTargetClass") // 方法名
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(Class.class) // 返回值Class<?>
                    .addParameter(String.class, "path") // 参数(String path)
                    .addStatement("return path.equals($S) ? $T.class : null",
                            aRouter.path(), ClassName.get((TypeElement) element))
                    .build(); // 构建

            // 构建类
            TypeSpec type = TypeSpec.classBuilder(finalClassName)
                    .addModifiers(Modifier.PUBLIC) //, Modifier.FINAL)
                    .addMethod(method) // 添加方法体
                    .build(); // 构建

            // 在指定的包名下，生成Java类文件
            JavaFile javaFile = JavaFile.builder(packageName, type)
                    .build();
            javaFile.writeTo(filer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

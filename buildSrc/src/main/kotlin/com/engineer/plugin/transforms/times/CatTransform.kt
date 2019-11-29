package com.engineer.plugin.transforms.times

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import org.apache.commons.io.FileUtils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * @author rookie
 * @since 11-29-2019
 */
class CatTransform : Transform() {
    override fun getName(): String {
        return "cat"
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    override fun isIncremental(): Boolean {
        return false
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)
        transformInvocation?.inputs?.forEach { input ->
            input.directoryInputs.forEach { directoryInput ->
                if (directoryInput.file.isDirectory) {
                    directoryInput.changedFiles.forEach {
                        val file = it.key
                        val name = file.name
                        if (name.endsWith(".class") && !(name == ("R.class"))
                            && !name.startsWith("R\$") && !(name == ("BuildConfig.class"))
                        ) {

                            val reader = ClassReader(file.readBytes())
                            val writer = ClassWriter(reader, ClassWriter.COMPUTE_MAXS)
                            val visitor = CatClassVisitor(writer)
                            reader.accept(visitor, ClassReader.EXPAND_FRAMES)

                            val code = writer.toByteArray()
                            val classPath = file.parentFile.absolutePath + File.separator + name
                            val fos = FileOutputStream(classPath)
                            fos.write(code)
                            fos.close()
                        }
                    }
                }

                val dest = transformInvocation?.outputProvider?.getContentLocation(
                    directoryInput.name,
                    directoryInput.contentTypes, directoryInput.scopes,
                    Format.DIRECTORY
                )


                FileUtils.copyDirectory(directoryInput.file, dest)
            }

            // todo 从 jar 文件里找到 class
            input.jarInputs.forEach { jarInput ->
                val src = jarInput.file
                val dest = transformInvocation?.outputProvider?.getContentLocation(
                    jarInput.name,
                    jarInput.contentTypes, jarInput.scopes, Format.JAR
                )

//                println("jar Name "+jarInput.name)
//                println("dest     "+dest.absolutePath)
//                println("jar path "+src.absolutePath)
//                println("jar name "+src.name)

                if (jarInput.name == "include") {

                    println("jar Name " + jarInput.name)
                    println("dest     " + dest?.absolutePath)
                    println("jar path " + src.absolutePath)
                    println("jar name " + src.name)

                    val temp =
                        src.absolutePath.substring(0, src.absolutePath.length - 4) + "_cat.jar"

                    val tempFile = File(temp)
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }

                    val outputStream = JarOutputStream(FileOutputStream(tempFile))


                    val jarFile = JarFile(src)
                    val entries = jarFile.entries()
                    while (entries.hasMoreElements()) {
                        val jarEntry = entries.nextElement()
                        val jarName = jarEntry.name
                        println("jarName==$jarName")


                        val inputStream = jarFile.getInputStream(jarEntry)
                        val zipEntry = ZipEntry(jarName)
                        outputStream.putNextEntry(zipEntry)

                        if (jarName.endsWith(".class") && jarName.startsWith("home")) {
                            val reader = ClassReader(inputStream)
                            val writer = ClassWriter(reader, ClassWriter.COMPUTE_MAXS)
                            val visitor = CatClassVisitor(writer)
                            reader.accept(visitor, ClassReader.EXPAND_FRAMES)
                            val code = writer.toByteArray()
                            outputStream.write(code)
                        } else {
                            println("unsupported jarName==$jarName")
                            var len = inputStream.read()
                            while (len != -1) {
                                outputStream.write(len)
                                len = inputStream.read()
                            }
                        }
                        inputStream.close()
                    }
                    outputStream.flush()
                    outputStream.close()
                    FileUtils.copyFile(tempFile, dest)
                    tempFile.delete()
//                    tempFile.renameTo(src)
                } else {

                    FileUtils.copyFile(src, dest)
                }

            }
        }
    }
}
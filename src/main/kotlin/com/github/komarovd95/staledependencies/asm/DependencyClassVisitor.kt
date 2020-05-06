package com.github.komarovd95.staledependencies.asm

import org.gradle.api.artifacts.ResolvedArtifact
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.TypePath
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

class DependencyClassVisitor(
        private val classesToArtifacts: Map<String, Set<ResolvedArtifact>>
) : ClassVisitor(Opcodes.ASM8) {

    val dependencies = LinkedHashSet<ResolvedArtifact>()

    private fun checkType(descriptor: String) {
        val type = Type.getType(descriptor)
        when (type.sort) {
            Type.ARRAY -> checkType(type.elementType.descriptor)
            Type.OBJECT -> addDependencies(type.internalName)
            else -> addDependencies(descriptor)
        }
    }

    override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<String?>?
    ) {
        if (superName != null) {
            addDependencies(superName)
        }
        interfaces?.forEach { iface ->
            if (iface != null) {
                addDependencies(iface)
            }
        }
        if (signature != null) {
            SignatureReader(signature).accept(DependencySignatureVisitor())
        }
    }

    override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: TypePath?,
            descriptor: String,
            visible: Boolean
    ): AnnotationVisitor {
        addDependencies(Type.getType(descriptor).internalName)
        return DependencyAnnotationVisitor()
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
        addDependencies(Type.getType(descriptor).internalName)
        return DependencyAnnotationVisitor()
    }

    override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
    ): FieldVisitor {
        checkType(descriptor)
        if (signature != null) {
            SignatureReader(signature).accept(DependencySignatureVisitor())
        }
        return DependencyFieldVisitor()
    }

    override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<String>?
    ): MethodVisitor {
        for (argumentType in Type.getArgumentTypes(descriptor)) {
            checkType(argumentType.descriptor)
        }
        checkType(Type.getReturnType(descriptor).descriptor)
        if (signature != null) {
            SignatureReader(signature).accept(DependencySignatureVisitor())
        }
        if (exceptions != null) {
            for (exception in exceptions) {
                addDependencies(exception)
            }
        }
        return DependencyMethodVisitor()
    }

    private fun addDependencies(className: String) {
        val artifacts = classesToArtifacts[className]
        if (artifacts != null) {
            dependencies.addAll(artifacts)
        }
    }

    private inner class DependencySignatureVisitor : SignatureVisitor(Opcodes.ASM8) {

        override fun visitClassType(name: String) {
            addDependencies(name)
        }
    }

    private inner class DependencyAnnotationVisitor : AnnotationVisitor(Opcodes.ASM8) {

        override fun visit(name: String, value: Any) {
            if (value is Type) {
                addDependencies(value.internalName)
            }
        }

        override fun visitEnum(name: String, descriptor: String, value: String) {
            addDependencies(Type.getType(descriptor).internalName)
        }
    }

    private inner class DependencyFieldVisitor : FieldVisitor(Opcodes.ASM8) {

        override fun visitTypeAnnotation(
                typeRef: Int,
                typePath: TypePath,
                descriptor: String,
                visible: Boolean
        ): AnnotationVisitor {
            addDependencies(Type.getType(descriptor).internalName)
            return DependencyAnnotationVisitor()
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
            addDependencies(Type.getType(descriptor).internalName)
            return DependencyAnnotationVisitor()
        }
    }

    private inner class DependencyMethodVisitor : MethodVisitor(Opcodes.ASM8) {

        override fun visitMethodInsn(
                opcode: Int,
                owner: String,
                name: String,
                descriptor: String,
                isInterface: Boolean
        ) {
            addDependencies(owner)
            for (argumentType in Type.getArgumentTypes(descriptor)) {
                checkType(argumentType.descriptor)
            }
            checkType(Type.getReturnType(descriptor).descriptor)
        }

        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
            checkType(descriptor)
        }

        override fun visitTypeInsn(opcode: Int, type: String) {
            addDependencies(type)
        }

        override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
            checkType(descriptor)
        }

        override fun visitLdcInsn(value: Any) {
            if (value is Type) {
                addDependencies(value.internalName)
            }
        }

        override fun visitInvokeDynamicInsn(
                name: String?,
                descriptor: String,
                bootstrapMethodHandle: Handle?,
                vararg bootstrapMethodArguments: Any?
        ) {
            checkType(descriptor)
        }

        override fun visitParameterAnnotation(parameter: Int, descriptor: String, visible: Boolean): AnnotationVisitor {
            addDependencies(Type.getType(descriptor).internalName)
            return DependencyAnnotationVisitor()
        }

        override fun visitTypeAnnotation(
                typeRef: Int,
                typePath: TypePath,
                descriptor: String,
                visible: Boolean
        ): AnnotationVisitor {
            addDependencies(Type.getType(descriptor).internalName)
            return DependencyAnnotationVisitor()
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
            addDependencies(Type.getType(descriptor).internalName)
            return DependencyAnnotationVisitor()
        }

        override fun visitTryCatchAnnotation(
                typeRef: Int,
                typePath: TypePath,
                descriptor: String,
                visible: Boolean
        ): AnnotationVisitor {
            addDependencies(Type.getType(descriptor).internalName)
            return DependencyAnnotationVisitor()
        }

        override fun visitLocalVariableAnnotation(
                typeRef: Int,
                typePath: TypePath,
                start: Array<Label>,
                end: Array<Label>,
                index: IntArray,
                descriptor: String,
                visible: Boolean
        ): AnnotationVisitor {
            addDependencies(Type.getType(descriptor).internalName)
            return DependencyAnnotationVisitor()
        }

        override fun visitInsnAnnotation(
                typeRef: Int,
                typePath: TypePath,
                descriptor: String,
                visible: Boolean
        ): AnnotationVisitor {
            addDependencies(Type.getType(descriptor).internalName)
            return DependencyAnnotationVisitor()
        }

        override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
            if (type != null) {
                addDependencies(type)
            }
        }

        override fun visitLocalVariable(
                name: String,
                descriptor: String,
                signature: String?,
                start: Label,
                end: Label,
                index: Int
        ) {
            checkType(descriptor)
            if (signature != null) {
                SignatureReader(signature).accept(DependencySignatureVisitor())
            }
        }
    }
}

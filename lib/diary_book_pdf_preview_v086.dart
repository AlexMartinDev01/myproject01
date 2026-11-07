import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:pdfx/pdfx.dart';

/// Full PDF pages are rendered locally from temporary in-memory bytes.
/// This screen does not save, upload, or transmit private diary content.
class DiaryBookPdfPreview extends StatefulWidget {
  const DiaryBookPdfPreview({super.key,required this.bytes});
  final Uint8List bytes;
  @override
  State<DiaryBookPdfPreview> createState()=>_DiaryBookPdfPreviewState();
}
class _DiaryBookPdfPreviewState extends State<DiaryBookPdfPreview> {
  late final PdfControllerPinch controller;
  @override
  void initState(){
    super.initState();
    controller=PdfControllerPinch(document:PdfDocument.openData(widget.bytes));
  }
  @override
  void dispose(){
    controller.dispose();
    super.dispose();
  }
  @override
  Widget build(BuildContext context)=>Scaffold(
    appBar:AppBar(title:const Text('PDF 日记书预览')),
    body:SafeArea(child:PdfViewPinch(controller:controller)),
    bottomNavigationBar:const SafeArea(top:false,
      child:Padding(padding:EdgeInsets.all(12),
        child:Text('仅本机预览 · 关闭后不保留预览文件',
          textAlign:TextAlign.center,style:TextStyle(fontSize:12)))),
  );
}

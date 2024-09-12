package com.naulian.composer

val COMPOSER_SAMPLE = """
    #1 heading 1
    #2 heading 2
    #3 heading 3
    #4 heading 4
    #5 heading 5
    #6 heading 6
   
    =line=
    
    `ignore ~syntax~ here`
    
    Lorem ipsum dolor sit amet, consectetur adipiscing elit. 
    
    Lorem ipsum dolor sit amet, consectetur adipiscing elit.
    
    <color this text#FF0000>
    
    this is &bold& text
    this is /italic/ text
    this is _underline_ text
    this is ~strikethrough~ text.
    date: %dd/MM/yyyy%
    
    Current time : ${dollarSign}time
    
    "this is quote text -author"
    
    {
    .kt
    fun main(varargs args: String) {
        println("Hello World!")
        val currentMillis = System.currentTimeMillis()
        println("Current time in millis: ${dollarSign}currentMillis")
        // output : ${dollarSign}millis
    }
    }
    
    \"this should not show quote\"
    
    {
    .py
    def main():
        print("Hello World!")
        
    if __name__ == '__main__':
        main()
    }
    
    Search (here@http://www.google.com) for anything.
    
    (img@https://picsum.photos/id/67/300/200)
    (ytb@https://www.youtube.com/watch?v=dQw4w9WgXcQ)
    
    [
    a    |b    |result
    true |true |true  
    true |false|false 
    &false&|false|false 
    ]
    
    * unordered item
    * unordered item
    
    *o uncheck item
    *x checked item
""".trimIndent()